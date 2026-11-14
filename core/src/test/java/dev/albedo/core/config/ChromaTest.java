package dev.albedo.core.config;

import dev.albedo.core.Category;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaTest {
    /** The turn of one speed at a time in seconds, from {@link Chroma#turns}. */
    private static double turn(int speed, double seconds) {
        float[] turns = new float[Chroma.TURN_FLOATS];
        Chroma.turns(seconds, turns);
        return turns[speed];
    }

    @Test
    void oneTurnTakesFrom120SecondsDownTo2() {
        assertEquals(120_000, Chroma.periodMs(1), 1e-6);
        assertEquals(2_000, Chroma.periodMs(100), 1e-6);
        assertTrue(Chroma.periodMs(30) < Chroma.periodMs(29));
    }

    @Test
    void theTurnMovesInEqualSmallStepsWithNoSteps() {
        Set<Double> seen = new HashSet<Double>();
        for (int frame = 1; frame < 120; frame++) {
            double before = turn(100, (frame - 1) / 60.0), now = turn(100, frame / 60.0);
            double step = now - before + (now < before ? 1 : 0);
            assertEquals(1 / 120.0, step, 1e-6, "60 frames each second for 2 s give one turn");
            seen.add(now);
        }
        assertEquals(119, seen.size(), "each frame has its own turn");
        assertEquals(0, turn(100, 2), 1e-6, "one turn ends where it started");
        assertEquals(0, turn(0, 3.3), "speed 0 stays still");
    }

    @Test
    void thePastelProfileHasSoftChromaColorsThatMove() {
        Profile pastel = Presets.pastel();
        Chroma blocks = pastel.get(Category.BLOCKS).appearance().chroma();
        assertEquals(13_300, Chroma.periodMs(blocks.speed()), 300, "the block colors take about 13 s for one turn");
        // Only the 1.21 versions keep chests in an atlas, so the block entities get no Chroma.
        assertFalse(pastel.get(Category.BLOCK_ENTITIES).appearance().chroma().isOn(), "the block entities have no Chroma");
        assertTrue(blocks.isOn() && blocks.strength() <= 60 && blocks.saturation() <= Chroma.DEFAULT_SATURATION, "the block colors stay soft");
    }

    @Test
    void turnsGivesTheTurnOfEachSpeedInWholeVec4Values() {
        assertEquals(0, Chroma.TURN_FLOATS % 4);
        assertTrue(Chroma.TURN_FLOATS > Chroma.MAX_SPEED);
        float[] turns = new float[Chroma.TURN_FLOATS];
        Chroma.turns(7.25, turns);
        assertEquals(0, turns[0]);
        for (int s = 1; s <= Chroma.MAX_SPEED; s++) {
            double t = 7.25 * 1000 / Chroma.periodMs(s);
            assertEquals(t - Math.floor(t), turns[s], 1e-6, "speed " + s);
        }
    }
}
