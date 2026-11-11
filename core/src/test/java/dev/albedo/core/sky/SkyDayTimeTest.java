package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkyDayTimeTest {
    private static void ticks(long... dayTimes) {
        for (long t : dayTimes) SkyDayTime.tick(t);
    }

    @Test
    void theFramesMoveBetweenTwoTicks() {
        ticks(1000, 1001);
        assertEquals(1000, SkyDayTime.at(0f), 1e-9);
        assertEquals(1000.25, SkyDayTime.at(0.25f), 1e-9);
        assertEquals(1001, SkyDayTime.at(1f), 1e-9);
        ticks(1002);
        assertEquals(1001.5, SkyDayTime.at(0.5f), 1e-9, "the next tick goes on from the last one");
    }

    @Test
    void aStoppedDaylightCycleStaysStill() {
        ticks(6000, 6000);
        assertEquals(6000, SkyDayTime.at(0f), 1e-9);
        assertEquals(6000, SkyDayTime.at(0.7f), 1e-9);
    }

    @Test
    void aJumpShowsAtOnce() {
        ticks(1000, 13000);
        assertEquals(13000, SkyDayTime.at(0.1f), 1e-9, "a time set forward");
        ticks(500);
        assertEquals(500, SkyDayTime.at(0.1f), 1e-9, "a time set back");
        ticks(500 + SkyDayTime.MAX_STEP);
        assertEquals(500 + SkyDayTime.MAX_STEP * 0.5, SkyDayTime.at(0.5f), 1e-9, "a fast clock still moves between the ticks");
    }
}
