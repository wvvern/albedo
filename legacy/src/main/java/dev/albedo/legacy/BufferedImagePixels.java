package dev.albedo.legacy;

import dev.albedo.core.PixelBuffer;

import java.awt.image.BufferedImage;

/** Adapts AWT images (what 1.8.9 loads textures into) to the core's ARGB pixel view. */
final class BufferedImagePixels implements PixelBuffer {
    private final BufferedImage image;

    BufferedImagePixels(BufferedImage image) {
        this.image = image;
    }

    /**
     * Copies palette, gray or alpha-less images to plain ARGB because they would lose our colors on write, and returns
     * the same instance for the 32-bit layouts, which setRGB writes exactly.
     */
    static BufferedImage toArgb(BufferedImage src) {
        if (src == null) return null;
        switch (src.getType()) {
            case BufferedImage.TYPE_INT_ARGB:
            case BufferedImage.TYPE_INT_ARGB_PRE:
            case BufferedImage.TYPE_4BYTE_ABGR:
            case BufferedImage.TYPE_4BYTE_ABGR_PRE:
                return src;
            default:
                break;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[src.getWidth()];
        for (int y = 0; y < src.getHeight(); y++) {
            src.getRGB(0, y, src.getWidth(), 1, row, 0, src.getWidth());
            out.setRGB(0, y, src.getWidth(), 1, row, 0, src.getWidth());
        }
        return out;
    }

    /** A new ARGB image with the pixels of {@code pixels}, for a repaint. */
    static BufferedImage toImage(PixelBuffer pixels) {
        int w = pixels.width(), h = pixels.height();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        if (pixels instanceof PixelBuffer.Heap) {
            out.setRGB(0, 0, w, h, ((PixelBuffer.Heap) pixels).argb(), 0, w);
        } else {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) out.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        return out;
    }

    @Override public int width() { return image.getWidth(); }
    @Override public int height() { return image.getHeight(); }
    @Override public int getArgb(int x, int y) { return image.getRGB(x, y); }
    @Override public void setArgb(int x, int y, int argb) { image.setRGB(x, y, argb); }
}
