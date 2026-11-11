package dev.albedo.core.sky;

import dev.albedo.core.config.SkyLayer;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SkyMeteorsTest {
    /** The program of the Meteors preset, whose fourth layer is the meteors. */
    private static final SkyProgram PROGRAM = SkyProgram.of(SkyPresets.METEORS, new float[SkyImage.RING * 3]);
    private static final int LAYER = 3;

    @Test
    void theSameSlotGivesTheSameMeteor() {
        int slot = 0;
        while (SkyMeteors.of(PROGRAM.block(), LAYER, slot, 0) == null) slot++;
        SkyMeteors.Meteor a = SkyMeteors.of(PROGRAM.block(), LAYER, slot, 0), b = SkyMeteors.of(PROGRAM.block(), LAYER, slot, 0);
        assertEquals(a.birth, b.birth);
        assertArrayEquals(a.start, b.start);
        assertArrayEquals(a.motion, b.motion);
        SkyMeteors.Meteor other = SkyMeteors.of(PROGRAM.block(), LAYER, slot + 1000, 0);
        assertTrue(other == null || other.start[0] != a.start[0], "another slot gives another meteor");
    }

    @Test
    void theMeteorsInTheSkyGoOnAcrossTheWrapOfTheClock() {
        int period = SkyMeteors.PERIOD_SLOTS, found = 0;
        assertEquals(SkyClock.PERIOD_MS / 1000, period * (long) SkyMeteors.SLOT, "the slots fill one period of the clock");
        for (int back = 1; back <= 600; back++) {
            for (int channel = 0; channel < SkyMeteors.CHANNELS; channel++) {
                SkyMeteors.Meteor before = SkyMeteors.of(PROGRAM.block(), LAYER, period - back, channel);
                SkyMeteors.Meteor after = SkyMeteors.of(PROGRAM.block(), LAYER, -back, channel);
                assertEquals(before == null, after == null, "slot " + -back + " has the meteor of slot " + (period - back));
                if (before == null) continue;
                found++;
                // The clock goes back by one period at the wrap, so a meteor in flight is at the same place after it.
                assertEquals(before.birth - period * SkyMeteors.SLOT, after.birth, 1e-3f);
                assertArrayEquals(before.start, after.start);
                assertArrayEquals(before.motion, after.motion);
            }
        }
        assertTrue(found > 0);
    }

    @Test
    void aMeteorIsAShortThinStreakHighInTheSky() {
        int count = 0, fireballs = 0;
        float low = (float) Math.sin(Math.toRadians(19.9));
        for (int slot = 0; slot < 2000; slot++) {
            for (int channel = 0; channel < SkyMeteors.CHANNELS; channel++) {
                SkyMeteors.Meteor m = SkyMeteors.of(PROGRAM.block(), LAYER, slot, channel);
                if (m == null) continue;
                count++;
                if (m.fireball) fireballs++;
                assertTrue(m.birth >= slot && m.birth < slot + 1, "a meteor starts in its slot");
                assertTrue(m.duration >= 0.3f && m.duration <= 1.4f, "a meteor lasts from 0.3 s to 1.4 s: " + m.duration);
                // At a field of view of 70, a trail of 3 to 10 degrees is a short streak, and a fireball is a little longer.
                double trail = Math.toDegrees(m.trail);
                assertTrue(trail >= 3 && trail <= (m.fireball ? 13.5 : 9.1), "trail " + trail);
                double speed = Math.toDegrees(m.speed);
                assertTrue(speed >= 16.5 && speed <= 31.5, "the speed at Speed 50 is about 24 degrees per second: " + speed);
                assertTrue(m.start[1] >= low, "a meteor starts high in the sky");
                assertEquals(0, dot(m.start, m.motion), 1e-5, "the motion is along the sky");
                assertEquals(1, dot(m.motion, m.motion), 1e-5);
                assertTrue(SkyMeteors.TRAIL_END * m.duration + 1 < SkyMeteors.SLOTS_BACK + 1, "the shader looks back far enough");
            }
        }
        // Amount 50 gives about 31 meteors in each minute in all of the sky.
        double perMinute = count / (2000 * SkyMeteors.SLOT / 60.0);
        assertEquals(SkyMeteors.perMinute(0.5f), perMinute, 4.0);
        assertTrue(fireballs > 0 && fireballs < count / 8, "fireballs are rare: " + fireballs + " of " + count);
    }

    @Test
    void showerMeteorsMoveAwayFromTheRadiant() {
        // The radiant is the layer center: turn 45 and tilt 55 of the preset.
        double yaw = Math.toRadians(45), pitch = Math.toRadians(55);
        float[] radiant = {(float) (Math.cos(pitch) * Math.sin(yaw)), (float) Math.sin(pitch), (float) (-Math.cos(pitch) * Math.cos(yaw))};
        int away = 0, count = 0;
        for (int slot = 0; slot < 1000; slot++) {
            for (int channel = 0; channel < SkyMeteors.CHANNELS; channel++) {
                SkyMeteors.Meteor m = SkyMeteors.of(PROGRAM.block(), LAYER, slot, channel);
                if (m == null) continue;
                count++;
                if (dot(m.motion, radiant) < -1e-4) away++;
            }
        }
        // Shower 70: a radiant meteor always moves away, and a random one does half of the time.
        assertTrue(away > count * 0.75, away + " of " + count);
    }

    @Test
    void theBakeHasNoMeteors() {
        SkyImage still = SkyGenerator.bake(SkyPresets.METEORS.withLayers(SkyPresets.METEORS.layers().subList(0, 3)), 128, 64);
        SkyImage withMeteors = SkyGenerator.bake(SkyPresets.METEORS, 128, 64);
        assertArrayEquals(still.argb(), withMeteors.argb(), "only the sky shader draws the meteors, so skycompare is the same");
        assertEquals(SkyLayer.Kind.METEORS, SkyPresets.METEORS.layers().get(LAYER).kind());
    }

    @Test
    void theShaderHasTheSameModel() throws IOException {
        String glsl = SkyProgramTest.skyLayersSource();
        // The numbers of SkyMeteors.of, which the shader must use too.
        String[] model = {"int(albedoLayer(layer, 2).y) * 7 + 13", "(2.0 + 118.0 * amount * amount)",
            "albedoMeteorUnit(slot, channel, 2, seed) < 0.05",
            "(0.3 + 0.7 * albedoMeteorUnit(slot, channel, 3, seed)) * (fireball ? 1.4 : 1.0)",
            "radians(8.0 + 32.0 * albedoLayer(layer, 4).w) * (0.7 + 0.6 * albedoMeteorUnit(slot, channel, 4, seed))",
            "sin(radians(20.0)), high = sin(radians(75.0))", "start * dot(start, radiant) - radiant",
            "albedoMeteorUnit(slot, channel, 9, seed) < albedoLayer(layer, 6).w", "albedoMeteorUnit(slot, channel, 10, seed)"};
        for (String s : model) assertTrue(glsl.contains(s), s);
    }

    private static double dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
