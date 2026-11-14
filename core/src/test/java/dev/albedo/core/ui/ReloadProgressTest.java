package dev.albedo.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReloadProgressTest {
    @Test
    void aReloadWithNoListenersHasNotStarted() {
        ReloadProgress p = new ReloadProgress();
        assertEquals(0f, p.progress());
        assertEquals("Starting", p.detail());
        assertEquals("Albedo: reloading resources", p.title());
    }

    @Test
    void eachListenerHasAnEqualPart() {
        ReloadProgress p = new ReloadProgress();
        p.begin(4);
        assertEquals(0f, p.progress());
        assertEquals("Step 1 of 4", p.detail());
        p.step();
        assertEquals(0f, p.progress());
        p.step();
        assertEquals(0.25f, p.progress());
        assertEquals("Step 2 of 4", p.detail());
    }

    @Test
    void loadsMoveTheBarHalfWayThroughAPartAt200() {
        ReloadProgress p = new ReloadProgress();
        p.begin(2);
        p.step();
        for (int i = 0; i < 200; i++) p.load();
        assertEquals(0.25f, p.progress(), 1e-6f);
        p.step();
        assertEquals(0.5f, p.progress(), "a step starts with no loads");
    }

    @Test
    void theProgressStopsAtTheEnd() {
        ReloadProgress p = new ReloadProgress();
        p.begin(1);
        p.step();
        p.step();
        for (int i = 0; i < 1000; i++) p.load();
        assertEquals(1f, p.progress());
    }
}
