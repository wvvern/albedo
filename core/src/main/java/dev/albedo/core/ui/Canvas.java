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

    /**
     * Draws an image stretched into [x, x + w) x [y, y + h), which an adapter keeps as one texture for each id.
     *
     * @param id the same for each frame that shows the same place, such as "sky.preview"
     */
    default void image(String id, CanvasImage img, int x, int y, int w, int h) {
        // Without textures, cells of about 4 GUI pixels show the image coarsely.
        int cell = 4;
        for (int cy = 0; cy < h; cy += cell) {
            for (int cx = 0; cx < w; cx += cell) {
                int cw = Math.min(cell, w - cx), ch = Math.min(cell, h - cy);
                int px = Math.min(img.width() - 1, (cx + cw / 2) * img.width() / w);
                int py = Math.min(img.height() - 1, (cy + ch / 2) * img.height() / h);
                fill(x + cx, y + cy, x + cx + cw, y + cy + ch, img.get(px, py) | 0xFF000000);
            }
        }
    }
}
