package dev.singlecolor.modern;

import com.mojang.blaze3d.platform.NativeImage;
import dev.singlecolor.core.PixelBuffer;
//? if <1.21.2 {
/*import dev.singlecolor.core.Colors;
*///?}

/** Adapts Minecraft's NativeImage to the core's ARGB pixel view. */
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
}
