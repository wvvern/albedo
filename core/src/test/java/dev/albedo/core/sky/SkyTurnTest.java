package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyTurning;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyTurnTest {
    /** A full turning with the pole on the north horizon, which turns as the vanilla sun does. */
    private static final SkyTurning SUN = new SkyTurning(100, 1, SkyTurning.Clock.WORLD, 0, SkyTurning.Direction.NORMAL, 0);

    @AfterEach
    void runTheClock() {
        SkyClock.run();
    }

    private static double[] toWorld(SkyTurn turn, double x, double y, double z) {
        float[] m = turn.matrix();
        return new double[]{m[0] * x + m[4] * y + m[8] * z, m[1] * x + m[5] * y + m[9] * z, m[2] * x + m[6] * y + m[10] * z};
    }

    @Test
    void aSkyWithNoAmountStaysStill() {
        assertSame(SkyTurn.STILL, SkyTurn.at(SkyTurning.STILL, 12345));
        assertSame(SkyTurn.STILL, SkyTurn.preview(SkyTurning.STILL));
        assertSame(SkyTurn.STILL, SkyTurn.at(SkyTurning.DEFAULT.withAmount(0), 12345));
        assertArrayEquals(new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}, SkyTurn.STILL.matrix());
    }

    @Test
    void theMatrixTurnsAroundThePoleAndKeepsLengthsAndAngles() {
        SkyTurning t = new SkyTurning(100, 1, SkyTurning.Clock.WORLD, 35, SkyTurning.Direction.NORMAL, 60);
        SkyTurn turn = SkyTurn.of(t, 0.3);
        float[] m = turn.matrix();
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                double dot = m[a * 4] * m[b * 4] + m[a * 4 + 1] * m[b * 4 + 1] + m[a * 4 + 2] * m[b * 4 + 2];
                assertEquals(a == b ? 1 : 0, dot, 1e-6, "columns " + a + " and " + b);
            }
        }
        double az = Math.toRadians(60), el = Math.toRadians(35);
        double[] pole = {Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)};
        assertArrayEquals(pole, toWorld(turn, pole[0], pole[1], pole[2]), 1e-6, "the pole stays in its place");
    }

    @Test
    void theTrailAxisTurnsAStarBackToWhereItWasEarlier() {
        for (SkyTurning.Direction dir : SkyTurning.Direction.values()) {
            SkyTurning t = new SkyTurning(100, 1, SkyTurning.Clock.WORLD, 35, dir, 60);
            double[] axis = SkyTurn.trailAxis(t);
            double[] star = {0.6, 0.48, -0.64};
            double turns = 0.3, back = 0.02;
            // The direction in the design where the world shows now the place of the star at an earlier time.
            double[] then = toWorld(SkyTurn.of(t, turns - back), star[0], star[1], star[2]);
            double[] seen = new double[3];
            SkyTurn.of(t, turns).toDesign(then[0], then[1], then[2], seen);
            assertArrayEquals(turn(star, axis, 2 * Math.PI * back), seen, 1e-6, dir.key());
        }
    }

    /** The vector {@code v} turned by {@code angle} around the unit {@code axis}, as the right hand turns. */
    private static double[] turn(double[] v, double[] axis, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle), d = (axis[0] * v[0] + axis[1] * v[1] + axis[2] * v[2]) * (1 - c);
        return new double[]{v[0] * c + (axis[1] * v[2] - axis[2] * v[1]) * s + axis[0] * d,
            v[1] * c + (axis[2] * v[0] - axis[0] * v[2]) * s + axis[1] * d,
            v[2] * c + (axis[0] * v[1] - axis[1] * v[0]) * s + axis[2] * d};
    }

    @Test
    void theNormalDirectionLiftsTheEastAsTheSunDoes() {
        double[] east = toWorld(SkyTurn.of(SUN, 0.01), 1, 0, 0);
        assertTrue(east[1] > 0.05, "the east side goes up: " + east[1]);
        double[] reverse = toWorld(SkyTurn.of(SUN.withDirection(SkyTurning.Direction.REVERSE), 0.01), 1, 0, 0);
        assertTrue(reverse[1] < -0.05, "the reverse direction takes it down: " + reverse[1]);
        double[] quarter = toWorld(SkyTurn.of(SUN, 0.25), 1, 0, 0);
        assertArrayEquals(new double[]{0, 1, 0}, quarter, 1e-6, "a quarter turn puts the east at the zenith");
    }

    @Test
    void theWorldClockFollowsTheSunAndShowsTheDesignAtMidnight() {
        assertEquals(0, SkyTurn.sunTurns(18000), 1e-9, "midnight");
        assertEquals(-0.5, SkyTurn.sunTurns(6000), 1e-9, "noon");
        assertEquals(0.5, SkyTurn.sunTurns(30000), 1e-9, "the next noon");
        assertArrayEquals(SkyTurn.STILL.matrix(), SkyTurn.at(SUN, 18000 + 24000 * 7).matrix(), 1e-6f);
        double[] zenith = toWorld(SkyTurn.at(SUN, 6000), 0, 1, 0);
        assertArrayEquals(new double[]{0, -1, 0}, zenith, 1e-6, "at noon the top of the design is below, as the moon is");
        double last = SkyTurn.sunTurns(0);
        for (int tick = 1; tick <= 48000; tick++) {
            double next = SkyTurn.sunTurns(tick);
            assertTrue(next > last && next - last < 0.0001, "the turns go up with no jump at tick " + tick);
            last = next;
        }
    }

    @Test
    void theSpeedAndTheAmountSetTheTurnsInOneDay() {
        SkyTurning t = SUN.withSpeed(4).withAmount(50);
        assertEquals(2, t.turnsPerDay(), 1e-9);
        assertArrayEquals(SkyTurn.at(t, 18000).matrix(), SkyTurn.at(t, 18000 + 12000).matrix(), 1e-5f, "two turns in a day");
        SkyClock.hold(0);
        SkyTurning real = t.withClock(SkyTurning.Clock.REAL);
        float[] start = SkyTurn.at(real, 0).matrix();
        SkyClock.hold((float) (SkyTurn.DAY_SECONDS / 2));
        assertArrayEquals(start, SkyTurn.at(real, 0).matrix(), 1e-5f, "one turn in half a real day");
        SkyClock.hold((float) (SkyTurn.DAY_SECONDS / 8));
        assertNotEquals(start[5], SkyTurn.at(real, 0).matrix()[5], 1e-3f, "the real clock turns the sky with no day time");
    }

    @Test
    void theFogTakesTheColorOfTheDesignPartAtTheHorizon() {
        // An image that is black at the top and red at the bottom, with a black ring, and a red that shows the elevation.
        int w = 64, h = 32;
        int[] argb = new int[w * h];
        for (int i = 0; i < argb.length; i++) argb[i] = 0xFF000000 | (i / w * 8) << 16;
        SkyImage img = new SkyImage(argb, w, h, SkyDesign.NONE, new float[SkyImage.RING * 3], null, 1, 1, new short[4]);
        assertEquals(0, img.fogRgb(SkyTurn.STILL, -90, 1)[0], 1e-6f);
        // A quarter turn around the north pole on the horizon puts the bottom of the design at the east horizon.
        float east = img.fogRgb(SkyTurn.of(SUN, 0.25), -90, 1)[0];
        assertEquals(248 / 255f, east, 0.01f, "the fog has the color of the last row, near the nadir of the design");
        // Around a pole at the zenith, the camera to the south sees the design part from the east, near the horizon.
        float south = img.fogRgb(SkyTurn.of(SUN.withPoleHeight(90), 0.25), 0, 1)[0];
        assertEquals(0.5f, south, 0.05f);
    }

    @Test
    void aStillResampleGivesThePixelsOfTheImage() {
        SkyImage img = SkyGenerator.bake(SkyPresets.NEBULA, 64, 32);
        int[] same = SkyTurn.STILL.resample(img, 32, 64, 32);
        assertArrayEquals(img.argb(), same);
        int[] turned = SkyTurn.of(SUN, 0.5).resample(img, 32, 64, 32);
        // Half a turn around the north pole on the horizon puts the bottom row of the design at the top, from east to west.
        assertEquals(img.argb()[31 * 64 + 63], turned[0]);
    }
}
