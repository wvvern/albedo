package dev.singlecolor.core;

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
            Heap heap = new Heap(src.width(), src.height());
            for (int y = 0; y < src.height(); y++)
                for (int x = 0; x < src.width(); x++)
                    heap.setArgb(x, y, src.getArgb(x, y));
            return heap;
        }

        @Override public int width() { return width; }
        @Override public int height() { return height; }
        @Override public int getArgb(int x, int y) { return pixels[y * width + x]; }
        @Override public void setArgb(int x, int y, int argb) { pixels[y * width + x] = argb; }
    }
}
