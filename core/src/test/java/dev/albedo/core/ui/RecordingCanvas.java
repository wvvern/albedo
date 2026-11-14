package dev.albedo.core.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Records the center of each drawn text and the last image of each id, and fails for anything drawn outside the screen. */
final class RecordingCanvas implements Canvas {
    final int w, h;
    final Map<String, int[]> texts = new LinkedHashMap<String, int[]>();
    final Map<String, CanvasImage> images = new LinkedHashMap<String, CanvasImage>();

    RecordingCanvas(int w, int h) {
        this.w = w;
        this.h = h;
    }

    @Override
    public void image(String id, CanvasImage img, int x, int y, int w, int h) {
        images.put(id, img);
    }

    /** The center of the text equal to {@code label}, else of the first text that contains it, else null. */
    int[] find(String label) {
        for (Map.Entry<String, int[]> e : texts.entrySet()) if (e.getKey().equals(label)) return e.getValue();
        for (Map.Entry<String, int[]> e : texts.entrySet()) if (e.getKey().contains(label)) return e.getValue();
        return null;
    }

    @Override public int width() { return w; }
    @Override public int height() { return h; }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        assertTrue(x0 >= 0 && y0 >= 0 && x1 <= w && y1 <= h, "fill outside the screen: " + x0 + "," + y0 + " " + x1 + "," + y1);
    }

    @Override
    public void text(String text, int x, int y, int argb) {
        assertTrue(x >= 0 && x + textWidth(text) <= w + 1 && y >= 0 && y + 9 <= h, "text outside the screen: " + text);
        texts.put(text, new int[]{x + textWidth(text) / 2, y + 4});
    }

    /** Approximates Minecraft's font: most glyphs 5px + 1 spacing, a few narrow ones. */
    @Override
    public int textWidth(String text) {
        int w = 0;
        for (char c : text.toCharArray()) {
            w += "il!.,:;'|".indexOf(c) >= 0 ? 2 : "It[] ".indexOf(c) >= 0 ? 4 : c == 'f' || c == 'k' ? 5 : 6;
        }
        return w;
    }
}
