package dev.albedo.core.sky;

import dev.albedo.core.config.SkyLayer;

import static dev.albedo.core.sky.SkyMath.smooth;

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
    static final float FIREBALL_LIFE = 1.4f, FIREBALL_TRAIL = 1.5f;
    /** The sine of the lowest and the highest elevation at which a meteor starts. */
    static final float LOW = (float) Math.sin(Math.toRadians(20)), HIGH = (float) Math.sin(Math.toRadians(75));

    /** One meteor in world directions, which are the sky directions of a sky that does not turn. */
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
        int seed = (int) block[SkyProgram.index(layer, SkyProgram.SEED)] * 7 + 13;
        float amount = block[SkyProgram.index(layer, SkyProgram.AMOUNT)];
        if (unit(slot, channel, 0, seed) >= perMinute(amount) * SLOT / 60f / CHANNELS) return null;
        Meteor m = new Meteor();
        m.birth = slot * SLOT + unit(slot, channel, 1, seed) * SLOT;
        m.fireball = unit(slot, channel, 2, seed) < FIREBALLS;
        m.duration = (0.3f + 0.7f * unit(slot, channel, 3, seed)) * (m.fireball ? FIREBALL_LIFE : 1f);
        float speed = block[SkyProgram.index(layer, SkyProgram.SPEED)];
        m.speed = (float) Math.toRadians(8f + 32f * speed) * (0.7f + 0.6f * unit(slot, channel, 4, seed));
        float length = block[SkyProgram.index(layer, SkyProgram.LENGTH)];
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
        float rx = -block[SkyProgram.index(layer, SkyProgram.LOCAL + 8)];
        float ry = -block[SkyProgram.index(layer, SkyProgram.LOCAL + 9)];
        float rz = -block[SkyProgram.index(layer, SkyProgram.LOCAL + 10)];
        float along = p[0] * rx + p[1] * ry + p[2] * rz;
        float ax = p[0] * along - rx, ay = p[1] * along - ry, az = p[2] * along - rz;
        float away = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        float shower = block[SkyProgram.index(layer, SkyProgram.SHOWER)];
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

    /**
     * Adds the meteors of a layer to the top rows of a bake as a long exposure shows them: the whole path of each head that
     * starts in {@code seconds} seconds, brighter to its end, because a bake has no meteors (each lives for about a second)
     * and a still image such as a card of the editor shows them only with this.
     *
     * @param layer the index of a meteors layer of the design of {@code img}
     * @param argb the top rows of {@code img}, which this method changes
     */
    public static void expose(SkyImage img, int layer, int seconds, int[] argb) {
        float[] block = img.program().block();
        SkyLayer l = img.design().layers().get(layer);
        int w = img.width(), h = img.height(), rows = argb.length / w;
        float[] light = new float[w * rows];
        // Steps of half a pixel leave no gap in a path.
        float step = (float) Math.PI / h / 2;
        for (int slot = 0; slot < seconds / SLOT; slot++) {
            for (int channel = 0; channel < CHANNELS; channel++) {
                Meteor m = of(block, layer, slot, channel);
                if (m == null) continue;
                float path = m.speed * m.duration, bright = m.fireball ? 1.5f : 0.8f;
                for (float a = 0; a <= path; a += step) {
                    float cos = (float) Math.cos(a), sin = (float) Math.sin(a), life = a / path;
                    float x = m.start[0] * cos + m.motion[0] * sin, y = m.start[1] * cos + m.motion[1] * sin;
                    float z = m.start[2] * cos + m.motion[2] * sin;
                    // The light of the head over its life, as in the shader, and nothing at the horizon.
                    float head = smooth(0f, 0.12f, life) * (1 - smooth(0.8f, 1f, life)) * (0.7f + 0.5f * life) * smooth(0f, 0.09f, y);
                    double u = Math.atan2(x, -z) / (2 * Math.PI) * w - 0.5;
                    double v = (0.5 - Math.asin(Math.max(-1, Math.min(1, y))) / Math.PI) * h - 0.5;
                    splat(light, w, rows, u, v, Math.min(1f, head * bright));
                }
            }
        }
        int color = l.param(SkyLayer.Param.COLOR_1);
        for (int i = 0; i < light.length; i++) {
            if (light[i] <= 0) continue;
            int out = 0xFF000000;
            for (int shift = 0; shift < 24; shift += 8) {
                int add = Math.round(light[i] * l.opacity() / 100f * (color >> shift & 0xFF));
                out |= Math.min(255, (argb[i] >> shift & 0xFF) + add) << shift;
            }
            argb[i] = out;
        }
    }

    /** Raises the four pixels around a place to their part of {@code value}, with a wrap across the sides, so that paths that cross do not add up. */
    private static void splat(float[] light, int w, int rows, double u, double v, float value) {
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = (float) (u - x0), fy = (float) (v - y0);
        for (int dy = 0; dy <= 1; dy++) {
            int y = y0 + dy;
            if (y < 0 || y >= rows) continue;
            for (int dx = 0; dx <= 1; dx++) {
                int i = y * w + Math.floorMod(x0 + dx, w);
                light[i] = Math.max(light[i], value * (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy));
            }
        }
    }

    private static float unit(int slot, int channel, int value, int seed) {
        return Noise.unit(Noise.hash(Math.floorMod(slot, PERIOD_SLOTS), channel, value, seed));
    }
}
