package dev.albedo.core.dev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorClipTest {
    private static String line(String probeOutput) {
        return CursorClip.parse(probeOutput).line();
    }

    @Test
    void aClipOfTheWholeScreenIsFree() {
        String line = line("0 0 3840 2160 0 0 3840 2160");
        assertTrue(line.startsWith("cursorfree: ok"), line);
    }

    @Test
    void aSmallerClipIsAnError() {
        String line = line("1120 630 2720 1530 0 0 3840 2160");
        assertTrue(line.startsWith("! cursorfree: the cursor is clipped"), line);
        assertTrue(line.contains("1600 x 900"), line);
    }

    @Test
    void aVirtualScreenThatDoesNotStartAtZeroIsFreeWhenTheClipIsAsLarge() {
        String line = line("-1920 0 1920 1080 -1920 0 3840 1080");
        assertTrue(line.startsWith("cursorfree: ok"), line);
    }

    @Test
    void theProbeOutputHasEightNumbers() {
        CursorClip.ClipProbe probe = CursorClip.parse("0 1 10 20 0 0 30 40\r\n");
        assertEquals(0, probe.clipLeft);
        assertEquals(1, probe.clipTop);
        assertEquals(10, probe.clipRight);
        assertEquals(20, probe.clipBottom);
        assertEquals(30, probe.screenWidth);
        assertEquals(40, probe.screenHeight);
        assertNull(CursorClip.parse("unreadable"));
        assertNull(CursorClip.parse("0 0 10 20 0 0 10 x"));
    }

    @Test
    void theLineKeepsItsFormat() {
        assertEquals("cursorfree: ok, clip 0,0,3840,2160 (3840 x 2160), screen 3840 x 2160", line("0 0 3840 2160 0 0 3840 2160"));
    }
}
