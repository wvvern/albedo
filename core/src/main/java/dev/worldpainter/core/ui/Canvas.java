package dev.worldpainter.core.ui;

/**
 * The drawing port for the settings screen: the few primitives every Minecraft version since 1.8 offers.
 * Coordinates are GUI-scaled pixels; colors are 0xAARRGGBB.
 */
public interface Canvas {
    int width();

    int height();

    /** Fills [x0, x1) x [y0, y1). */
    void fill(int x0, int y0, int x1, int y1, int argb);

    /** Draws text (no shadow) with its top-left at x, y; the font is 9 pixels high. */
    void text(String text, int x, int y, int argb);

    int textWidth(String text);
}
