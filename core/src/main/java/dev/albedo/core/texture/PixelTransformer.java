package dev.albedo.core.texture;

import dev.albedo.core.Colors;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.config.Appearance;

/** Applies an {@link Appearance} to an image in place, as a pure function of its inputs. */
public final class PixelTransformer {
    /** The {@link #average} of a fully transparent image. */
    private static final int MID_GRAY = 0xFF808080;
    /** The Rec. 601 luma weights in thousandths, which {@link #grayscale} uses. */
    private static final int LUMA_R = 299, LUMA_G = 587, LUMA_B = 114, LUMA_SUM = 1000;

    private PixelTransformer() {}

    /** @return true if the image was changed */
    public static boolean apply(Appearance a, PixelBuffer img) {
        switch (a.mode()) {
            case ORIGINAL:
                return false;
            case HIDDEN:
                fillAll(img, 0x00000000);
                return true;
            case SOLID:
                fillShape(img, a.color(), a.alpha());
                return true;
            case RECOLOR:
                Recolor.apply(a, img);
                return true;
            case AVERAGE:
                fillShape(img, average(img), a.alpha());
                return true;
            case GRAYSCALE:
                grayscale(img, a.alpha());
                return true;
            default:
                return false;
        }
    }

    /**
     * The opaque color of a swatch for {@code original} with the style of {@code a}, and its first Chroma color when
     * {@code id} is known and Chroma colors the whole texture, not only its Edge.
     */
    public static int preview(Appearance a, int original, String id) {
        PixelBuffer.Heap pixel = new PixelBuffer.Heap(1, 1, new int[]{original | 0xFF000000});
        Appearance opaque = a.withAlpha(Appearance.Alpha.OPAQUE);
        apply(opaque, pixel);
        if (id != null && !a.chroma().edge()) Effects.apply(opaque, pixel, id, false, false, false);
        return pixel.getArgb(0, 0);
    }

    /** Sets every pixel to {@code color}. */
    private static void fillAll(PixelBuffer img, int color) {
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) img.setArgb(x, y, color);
        }
    }

    /** Fills every pixel with {@code color}, where the alpha follows the source pixel's alpha per {@code alphaMode}, times the color's alpha. */
    static void fillShape(PixelBuffer img, int color, Appearance.Alpha alphaMode) {
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) {
                int a = shapeAlpha(Colors.alpha(img.getArgb(x, y)), alphaMode);
                img.setArgb(x, y, Colors.withAlpha(color, withColorAlpha(a, color)));
            }
        }
    }

    /** The output alpha of a pixel: its shape alpha times the alpha of the look's color. */
    static int shapedAlpha(int srcAlpha, Appearance a) {
        return withColorAlpha(shapeAlpha(srcAlpha, a.alpha()), a.color());
    }

    private static int withColorAlpha(int alpha, int color) {
        return alpha * Colors.alpha(color) / 255;
    }

    /** The step of a grid of at most about 4096 samples, by area, so that a tall animation strip is sampled across its width too. */
    static int sampleStep(PixelBuffer img) {
        return Math.max(1, (int) Math.sqrt((double) img.width() * img.height() / 4096));
    }

    static int shapeAlpha(int srcAlpha, Appearance.Alpha mode) {
        switch (mode) {
            case OPAQUE: return 255;
            case BINARY: return srcAlpha >= 128 ? 255 : 0;
            default: return srcAlpha;
        }
    }

    /** Opaque alpha-weighted average of the visible pixels, or mid gray for a fully transparent image. */
    public static int average(PixelBuffer img) {
        long r = 0, g = 0, b = 0, w = 0;
        int step = sampleStep(img);
        for (int y = 0; y < img.height(); y += step) {
            for (int x = 0; x < img.width(); x += step) {
                int p = img.getArgb(x, y);
                int a = Colors.alpha(p);
                if (a == 0) continue;
                r += (long) Colors.red(p) * a;
                g += (long) Colors.green(p) * a;
                b += (long) Colors.blue(p) * a;
                w += a;
            }
        }
        if (w == 0) return MID_GRAY;
        return Colors.argb(255, (int) (r / w), (int) (g / w), (int) (b / w));
    }

    static void grayscale(PixelBuffer img, Appearance.Alpha alphaMode) {
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) {
                int p = img.getArgb(x, y);
                int l = (Colors.red(p) * LUMA_R + Colors.green(p) * LUMA_G + Colors.blue(p) * LUMA_B) / LUMA_SUM;
                img.setArgb(x, y, Colors.argb(shapeAlpha(Colors.alpha(p), alphaMode), l, l, l));
            }
        }
    }
}
