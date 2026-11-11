package dev.albedo.core.dev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorClipTest {
    @Test
    void aClipOfTheWholeScreenIsFree() {
        String line = CursorClip.line(new int[]{0, 0, 3840, 2160, 0, 0, 3840, 2160});
        assertTrue(line.startsWith("cursorfree: ok"), line);
    }

    @Test
    void aSmallerClipIsAnError() {
        String line = CursorClip.line(new int[]{1120, 630, 2720, 1530, 0, 0, 3840, 2160});
        assertTrue(line.startsWith("! cursorfree: the cursor is clipped"), line);
        assertTrue(line.contains("1600 x 900"), line);
    }

    @Test
    void aVirtualScreenThatDoesNotStartAtZeroIsFreeWhenTheClipIsAsLarge() {
        String line = CursorClip.line(new int[]{-1920, 0, 1920, 1080, -1920, 0, 3840, 1080});
        assertTrue(line.startsWith("cursorfree: ok"), line);
    }

    @Test
    void theProbeOutputHasEightNumbers() {
        assertArrayEquals(new int[]{0, 0, 10, 20, 0, 0, 10, 20}, CursorClip.parse("0 0 10 20 0 0 10 20\r\n"));
        assertNull(CursorClip.parse("unreadable"));
        assertNull(CursorClip.parse("0 0 10 20 0 0 10 x"));
    }
}
