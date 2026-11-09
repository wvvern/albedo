package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyClockTest {
    @Test
    void theClockRunsOnFromTheHeldTime() throws InterruptedException {
        try {
            SkyClock.hold(1234.5f);
            assertEquals(1234.5f, SkyClock.seconds());
            SkyClock.run();
            Thread.sleep(20);
            float now = SkyClock.seconds();
            assertTrue(now > 1234.5f && now < 1240f, "the clock does not jump when it runs again: " + now);
            assertEquals(now, SkyClock.longSeconds(), 1.0, "the clock with no wrap runs on from the same time");
            SkyClock.run();
            assertEquals(now, SkyClock.seconds(), 1.0, "a second run does not move the clock");
        } finally {
            SkyClock.run();
        }
    }
}
