package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyDeferTest {
    @Test
    void theSkyWaitsOnlyAfterAFrameInWhichTheHookRan() {
        SkyDefer<String> defer = new SkyDefer<String>();
        defer.frameStart();
        assertFalse(defer.defer("first"), "the first frame draws in the sky pass");
        assertNull(defer.take());
        defer.frameStart();
        assertTrue(defer.defer("second"));
        assertEquals("second", defer.take());
        assertNull(defer.take(), "a second hook in the same frame draws nothing");
    }

    @Test
    void aFrameWithNoHookPutsTheSkyBackInTheSkyPass() {
        SkyDefer<String> defer = new SkyDefer<String>();
        defer.frameStart();
        defer.take();
        defer.frameStart();
        assertTrue(defer.defer("lost"), "the hook does not run in this frame");
        defer.frameStart();
        assertFalse(defer.defer("next"), "the next frame draws in the sky pass");
        assertNull(defer.take());
        defer.frameStart();
        assertTrue(defer.defer("again"), "the hook ran again");
    }

    @Test
    void aSkyThatWaitsNeverGoesToTheNextFrame() {
        SkyDefer<String> defer = new SkyDefer<String>();
        defer.frameStart();
        defer.take();
        defer.frameStart();
        defer.defer("old");
        defer.frameStart();
        assertNull(defer.take(), "a frame with no sky pass draws nothing after the solid world");
    }
}
