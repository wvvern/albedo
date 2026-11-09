package dev.albedo.core.ui;

/** An immutable ARGB image for {@link Canvas#image}, whose serial changes when its pixels change, so that an adapter uploads it once. */
public final class CanvasImage {
    private final int[] argb;
    private final int width, height;
    private final long serial;

    /** @param argb the pixels row by row from the top, which nobody changes after this call */
    public CanvasImage(int[] argb, int width, int height, long serial) {
        if (argb.length != width * height) throw new IllegalArgumentException("pixel count mismatch");
        this.argb = argb;
        this.width = width;
        this.height = height;
        this.serial = serial;
    }

    public int width() { return width; }

    public int height() { return height; }

    public long serial() { return serial; }

    /** The pixel at x, y as 0xAARRGGBB. */
    public int get(int x, int y) {
        return argb[y * width + x];
    }

    /** The pixels row by row from the top, which the caller must not change. */
    public int[] argb() { return argb; }
}
