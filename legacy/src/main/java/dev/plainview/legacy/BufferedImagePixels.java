package dev.plainview.legacy;

import dev.plainview.core.PixelBuffer;

import java.awt.image.BufferedImage;

/** Adapts AWT images (what 1.8.9 loads textures into) to the core's ARGB pixel view. */
final class BufferedImagePixels implements PixelBuffer {
    private final BufferedImage image;

    BufferedImagePixels(BufferedImage image) {
        this.image = image;
    }

    /**
     * Palette or byte-backed images would quantise our colors on write, so convert to plain ARGB first.
     * Returns the same instance when it already is.
     */
    static BufferedImage toArgb(BufferedImage src) {
        if (src == null || src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[src.getWidth()];
        for (int y = 0; y < src.getHeight(); y++) {
            src.getRGB(0, y, src.getWidth(), 1, row, 0, src.getWidth());
            out.setRGB(0, y, src.getWidth(), 1, row, 0, src.getWidth());
        }
        return out;
    }

    @Override public int width() { return image.getWidth(); }
    @Override public int height() { return image.getHeight(); }
    @Override public int getArgb(int x, int y) { return image.getRGB(x, y); }
    @Override public void setArgb(int x, int y, int argb) { image.setRGB(x, y, argb); }
}
