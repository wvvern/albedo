package dev.albedo.core.ui;

/** The drawing primitives of every Minecraft version since 1.8, in GUI pixels and 0xAARRGGBB colors. */
public interface Canvas {
    int width();

    int height();

    /** Fills [x0, x1) x [y0, y1). */
    void fill(int x0, int y0, int x1, int y1, int argb);

    /** Draws text (no shadow) with its top-left at x, y; the font is 9 pixels high. */
    void text(String text, int x, int y, int argb);

    int textWidth(String text);
}
