package dev.albedo.core.config;

import dev.albedo.core.Category;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaTest {
    /** The turn of one speed at a time in seconds, from {@link Chroma#turns}. */
    private static double turn(int speed, double seconds) {
        float[] turns = new float[Chroma.TURN_FLOATS];
        Chroma.turns(seconds, turns);
        return turns[speed];
    }

    @Test
    void oneTurnTakesFrom120SecondsDownTo10() {
        assertEquals(120_000, Chroma.periodMs(1), 1e-6);
        assertEquals(10_000, Chroma.periodMs(100), 1e-6);
        assertTrue(Chroma.periodMs(30) < Chroma.periodMs(29));
    }

    @Test
    void theTurnMovesInEqualSmallStepsWithNoSteps() {
        Set<Double> seen = new HashSet<Double>();
        for (int frame = 1; frame < 600; frame++) {
            double before = turn(100, (frame - 1) / 60.0), now = turn(100, frame / 60.0);
            double step = now - before + (now < before ? 1 : 0);
            assertEquals(1 / 600.0, step, 1e-6, "60 frames each second for 10 s give one turn");
            seen.add(now);
        }
        assertEquals(599, seen.size(), "each frame has its own turn");
        assertEquals(0, turn(100, 10), 1e-6, "one turn ends where it started");
        assertEquals(0, turn(0, 3.3), "speed 0 stays still");
    }

    @Test
    void thePastelProfileHasSoftChromaColorsThatMoveSlowly() {
        Profile pastel = Presets.pastel();
        for (Category category : new Category[]{Category.BLOCKS, Category.BLOCK_ENTITIES}) {
            Chroma c = pastel.get(category).appearance().chroma();
            assertTrue(c.moves(), category + " colors move");
            assertTrue(Chroma.periodMs(c.speed()) >= 90_000, category + " colors take at least 90 s for one turn");
            assertTrue(c.strength() <= 60 && c.saturation() <= Chroma.DEFAULT_SATURATION, category + " colors stay soft");
        }
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
