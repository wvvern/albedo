package dev.albedo.core.config;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaTest {
    @Test
    void oneTurnTakesFrom120SecondsDownTo10() {
        assertEquals(120_000, new Chroma(100, 1, 50, 50).periodMs(), 1e-6);
        assertEquals(10_000, new Chroma(100, 100, 50, 50).periodMs(), 1e-6);
        assertTrue(new Chroma(100, 30, 50, 50).periodMs() < new Chroma(100, 29, 50, 50).periodMs());
    }

    @Test
    void theTurnMovesInEqualSmallStepsWithNoSteps() {
        Chroma c = new Chroma(100, 100, 50, 50);
        Set<Double> seen = new HashSet<Double>();
        for (int frame = 1; frame < 600; frame++) {
            double before = c.turn((frame - 1) / 60.0), now = c.turn(frame / 60.0);
            double step = now - before + (now < before ? 1 : 0);
            assertEquals(1 / 600.0, step, 1e-9, "60 frames each second for 10 s give one turn");
            seen.add(now);
        }
        assertEquals(599, seen.size(), "each frame has its own turn");
        assertEquals(0, c.turn(10), 1e-9, "one turn ends where it started");
        assertEquals(0, new Chroma(100, 0, 50, 50).turn(3.3), "speed 0 stays still");
        assertEquals(0, new Chroma(0, 50, 50, 50).turn(3.3), "Chroma off stays still");
    }

    @Test
    void turnsGivesTheTurnOfEachSpeed() {
        float[] turns = new float[Chroma.MAX_SPEED / 4 * 4 + 4];
        Chroma.turns(7.25, turns);
        assertEquals(0, turns[0]);
        for (int s = 1; s <= Chroma.MAX_SPEED; s++) assertEquals(new Chroma(100, s, 50, 50).turn(7.25), turns[s], 1e-6, "speed " + s);
    }
}
