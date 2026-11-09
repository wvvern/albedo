package dev.albedo.core.sky;

/**
 * The shooting stars of a meteors layer, the same model as {@code albedoMeteor} in the sky shader: each slot of time has
 * some channels, and the hash of a slot, a channel and the seed tells whether a meteor starts in it and where it goes,
 * so a meteor needs no state.
 */
public final class SkyMeteors {
    /** The length of one slot of time in seconds. */
    static final float SLOT = 1f;
    /** The largest number of meteors that start in one slot. */
    static final int CHANNELS = 3;
    /** The slots before the current one that can still have a meteor in the sky, because the longest one lasts less than 2 s. */
    static final int SLOTS_BACK = 2;
    /** The slots in one period of {@link SkyClock}, after which the hashes repeat, so that the meteors go on across the wrap of the time. */
    static final int PERIOD_SLOTS = (int) (SkyClock.PERIOD_MS / 1000 / SLOT);
    /** The part of its life after which the trail of a meteor is gone; the head goes out at 1. */
    static final float TRAIL_END = 1.15f;
    /** The part of the meteors that are fireballs: brighter, longer and slower to go out. */
    static final float FIREBALLS = 0.05f;
    private static final float FIREBALL_LIFE = 1.4f, FIREBALL_TRAIL = 1.5f;
    /** The sine of the lowest and the highest elevation at which a meteor starts. */
    private static final float LOW = (float) Math.sin(Math.toRadians(20)), HIGH = (float) Math.sin(Math.toRadians(75));

    /** One meteor in sky directions. */
    static final class Meteor {
        float birth;
        /** The seconds that the head moves. */
        float duration;
        /** The speed of the head in radians per second. */
        float speed;
        /** The length of a full trail in radians. */
        float trail;
        boolean fireball;
        /** The unit direction where the meteor starts. */
        final float[] start = new float[3];
        /** The unit direction of the motion at the start, at a right angle to it. */
        final float[] motion = new float[3];

        /** The direction of the head at a time, which stops where the meteor goes out. */
        void head(float time, float[] out) {
            float angle = speed * Math.max(0f, Math.min(duration, time - birth));
            float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
            for (int i = 0; i < 3; i++) out[i] = start[i] * c + motion[i] * s;
        }
    }

    private SkyMeteors() {}

    /** The meteors that start in one minute, from AMOUNT from 0 to 1. */
    static float perMinute(float amount) {
        return 2f + 118f * amount * amount;
    }

    /**
     * The meteor of a slot and a channel of layer {@code layer} of a program block, or null when none starts there.
     */
    static Meteor of(float[] block, int layer, int slot, int channel) {
        int seed = (int) block[SkyProgram.at(layer, SkyProgram.SEED)] * 7 + 13;
        float amount = block[SkyProgram.at(layer, SkyProgram.AMOUNT)];
        if (unit(slot, channel, 0, seed) >= perMinute(amount) * SLOT / 60f / CHANNELS) return null;
        Meteor m = new Meteor();
        m.birth = slot * SLOT + unit(slot, channel, 1, seed) * SLOT;
        m.fireball = unit(slot, channel, 2, seed) < FIREBALLS;
        m.duration = (0.3f + 0.7f * unit(slot, channel, 3, seed)) * (m.fireball ? FIREBALL_LIFE : 1f);
        float speed = block[SkyProgram.at(layer, SkyProgram.SPEED)];
        m.speed = (float) Math.toRadians(8f + 32f * speed) * (0.7f + 0.6f * unit(slot, channel, 4, seed));
        float length = block[SkyProgram.at(layer, SkyProgram.LENGTH)];
        m.trail = (float) Math.toRadians(3f + 7f * length) * (0.75f + 0.5f * unit(slot, channel, 5, seed)) * (m.fireball ? FIREBALL_TRAIL : 1f);
        // Hash value 6 is the brightness, which only the shader uses.
        float azimuth = (float) (2 * Math.PI) * unit(slot, channel, 7, seed);
        float y = LOW + (HIGH - LOW) * unit(slot, channel, 8, seed);
        float c = (float) Math.sqrt(1 - y * y);
        m.start[0] = c * (float) Math.sin(azimuth);
        m.start[1] = y;
        m.start[2] = -c * (float) Math.cos(azimuth);
        float[] p = m.start;
        // A shower meteor moves away from the radiant, the layer center: the -z axis of the layer frame.
        float rx = -block[SkyProgram.at(layer, SkyProgram.LOCAL + 8)];
        float ry = -block[SkyProgram.at(layer, SkyProgram.LOCAL + 9)];
        float rz = -block[SkyProgram.at(layer, SkyProgram.LOCAL + 10)];
        float along = p[0] * rx + p[1] * ry + p[2] * rz;
        float ax = p[0] * along - rx, ay = p[1] * along - ry, az = p[2] * along - rz;
        float away = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        float shower = block[SkyProgram.at(layer, SkyProgram.SHOWER)];
        if (unit(slot, channel, 9, seed) < shower && away > 1e-3f) {
            m.motion[0] = ax / away;
            m.motion[1] = ay / away;
            m.motion[2] = az / away;
        } else {
            // A random direction in the plane at a right angle to the start, from the east and the north there.
            float turn = (float) (2 * Math.PI) * unit(slot, channel, 10, seed);
            float ex = p[2] / c, ez = -p[0] / c;
            float nx = p[1] * ez, ny = p[2] * ex - p[0] * ez, nz = -p[1] * ex;
            float cos = (float) Math.cos(turn), sin = (float) Math.sin(turn);
            m.motion[0] = ex * cos + nx * sin;
            m.motion[1] = ny * sin;
            m.motion[2] = ez * cos + nz * sin;
        }
        return m;
    }

    private static float unit(int slot, int channel, int value, int seed) {
        return Noise.unit(Noise.hash(Math.floorMod(slot, PERIOD_SLOTS), channel, value, seed));
    }

    /**
     * The first time after {@code from} at which a meteor of the first shown meteors layer is at its brightest above
     * {@code minElevation} degrees, with the direction of its head.
     *
     * @return {time, x, y, z} in sky directions, or null when the program has no meteors in the next ten minutes
     */
    public static float[] nextPeak(SkyProgram program, float from, float minElevation) {
        int layer = -1;
        float[] block = program.block();
        for (int i = 0; i < (int) block[0] && layer < 0; i++) {
            if ((int) block[SkyProgram.at(i, SkyProgram.KIND)] == SkyProgram.KIND_METEORS
                    && (int) block[SkyProgram.at(i, SkyProgram.USE)] == SkyProgram.USE_SHOW) {
                layer = i;
            }
        }
        if (layer < 0) return null;
        float low = (float) Math.sin(Math.toRadians(minElevation));
        float[] head = new float[3];
        int first = (int) Math.floor(from / SLOT);
        for (int slot = first; slot < first + 600; slot++) {
            for (int channel = 0; channel < CHANNELS; channel++) {
                Meteor m = of(block, layer, slot, channel);
                if (m == null) continue;
                float peak = m.birth + 0.7f * m.duration;
                if (peak <= from) continue;
                m.head(peak, head);
                if (head[1] >= low) return new float[]{peak, head[0], head[1], head[2]};
            }
        }
        return null;
    }
}
