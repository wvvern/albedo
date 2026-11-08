package dev.blockpainter.core;

/**
 * Minimal mutable image view. Adapters wrap the platform image type (BufferedImage, NativeImage...) in this,
 * converting to and from non-premultiplied ARGB.
 */
public interface PixelBuffer {
    int width();

    int height();

    /** @return the pixel as 0xAARRGGBB */
    int getArgb(int x, int y);

    void setArgb(int x, int y, int argb);

    /** Simple heap-backed implementation, used for user textures and tests. */
    final class Heap implements PixelBuffer {
        private final int width;
        private final int height;
        private final int[] pixels;

        public Heap(int width, int height) {
            this(width, height, new int[width * height]);
        }

        public Heap(int width, int height, int[] pixels) {
            if (pixels.length != width * height) throw new IllegalArgumentException("pixel count mismatch");
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }

        public static Heap copyOf(PixelBuffer src) {
            if (src instanceof Heap) return ((Heap) src).copy();
            Heap heap = new Heap(src.width(), src.height());
            for (int y = 0; y < src.height(); y++) {
                for (int x = 0; x < src.width(); x++) heap.setArgb(x, y, src.getArgb(x, y));
            }
            return heap;
        }

        public Heap copy() {
            return new Heap(width, height, pixels.clone());
        }

        /** Writes the pixels into {@code target}, which has the same length. */
        public void copyInto(int[] target) {
            System.arraycopy(pixels, 0, target, 0, pixels.length);
        }

        /** The pixels, row by row from the top, as 0xAARRGGBB. Read-only use: this is the live array. */
        public int[] argb() {
            return pixels;
        }

        @Override public int width() { return width; }
        @Override public int height() { return height; }
        @Override public int getArgb(int x, int y) { return pixels[y * width + x]; }
        @Override public void setArgb(int x, int y, int argb) { pixels[y * width + x] = argb; }
    }
}
