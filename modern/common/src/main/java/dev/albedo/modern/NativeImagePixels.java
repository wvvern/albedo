package dev.albedo.modern;

import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.PixelBuffer;
//? if <1.21.2 {
/*import dev.albedo.core.Colors;
*///?}

public final class NativeImagePixels implements PixelBuffer {
    private final NativeImage image;

    public NativeImagePixels(NativeImage image) {
        this.image = image;
    }

    /** Only 4-channel images can be read/written per pixel. */
    public static boolean supports(NativeImage image) {
        return image != null && image.format() == NativeImage.Format.RGBA;
    }

    @Override public int width() { return image.getWidth(); }
    @Override public int height() { return image.getHeight(); }

    @Override
    public int getArgb(int x, int y) {
        //? if >=1.21.2 {
        return image.getPixel(x, y);
        //?} else {
        /*return Colors.swapRedBlue(image.getPixelRGBA(x, y));
        *///?}
    }

    @Override
    public void setArgb(int x, int y, int argb) {
        //? if >=1.21.2 {
        image.setPixel(x, y, argb);
        //?} else {
        /*image.setPixelRGBA(x, y, Colors.swapRedBlue(argb));
        *///?}
    }

    /** All pixels as ARGB, row by row from the top. */
    public int[] getAll() {
        int w = width(), h = height();
        int[] argb = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) argb[y * w + x] = getArgb(x, y);
        }
        return argb;
    }

    /** Sets all pixels from ARGB, row by row from the top. */
    public void setAll(int[] argb) {
        int w = width(), h = height();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) setArgb(x, y, argb[y * w + x]);
        }
    }
}
