package dev.albedo.core.texture;

import dev.albedo.core.Colors;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.config.Appearance;

/** Applies an {@link Appearance} to an image in place, as a pure function of its inputs. */
public final class PixelTransformer {
    /** Supplies user images for {@link Appearance.Mode#TEXTURE}, or null when the file is missing. */
    public interface TextureSource {
        PixelBuffer load(String name);
    }

    private PixelTransformer() {}

    /** @return true if the image was changed */
    public static boolean apply(Appearance a, PixelBuffer img, TextureSource textures) {
        switch (a.mode()) {
            case ORIGINAL:
                return false;
            case HIDDEN:
                fill(img, 0x00000000, Appearance.Alpha.OPAQUE, false);
                return true;
            case SOLID:
                fill(img, a.color(), a.alpha(), true);
                return true;
            case RECOLOR:
                Recolor.apply(a, img);
                return true;
            case AVERAGE:
                fill(img, average(img), a.alpha(), true);
                return true;
            case GRAYSCALE:
                grayscale(img, a.alpha());
                return true;
            case TEXTURE: {
                PixelBuffer src = textures == null ? null : textures.load(a.texture());
                if (src == null || src.width() == 0 || src.height() == 0) {
                    fill(img, a.color(), a.alpha(), true);
                } else {
                    stretch(src, img, a.alpha());
                }
                return true;
            }
            default:
                return false;
        }
    }

    /** The opaque color of a swatch for {@code original} with the style of {@code a}, and its Chroma when {@code id} is known. */
    public static int preview(Appearance a, int original, String id) {
        PixelBuffer.Heap pixel = new PixelBuffer.Heap(1, 1, new int[]{original | 0xFF000000});
        Appearance opaque = a.withAlpha(Appearance.Alpha.OPAQUE);
        apply(opaque, pixel, null);
        if (id != null) Effects.apply(opaque, pixel, id, false);
        return pixel.getArgb(0, 0);
    }

    /**
     * Fills every pixel with {@code color}, and with {@code respectShape} the output alpha follows the source pixel's
     * alpha per {@code alphaMode} (multiplied by the color's own alpha).
     */
    static void fill(PixelBuffer img, int color, Appearance.Alpha alphaMode, boolean respectShape) {
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) {
                int a = respectShape ? shapeAlpha(Colors.alpha(img.getArgb(x, y)), alphaMode) : 255;
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
        if (w == 0) return 0xFF808080;
        return Colors.argb(255, (int) (r / w), (int) (g / w), (int) (b / w));
    }

    static void grayscale(PixelBuffer img, Appearance.Alpha alphaMode) {
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) {
                int p = img.getArgb(x, y);
                int l = (Colors.red(p) * 299 + Colors.green(p) * 587 + Colors.blue(p) * 114) / 1000;
                img.setArgb(x, y, Colors.argb(shapeAlpha(Colors.alpha(p), alphaMode), l, l, l));
            }
        }
    }

    /**
     * Nearest-neighbour stretch of {@code src} over {@code dst} that gives tall animation strips (height a multiple of
     * width) the image once per square frame, so that every frame looks the same.
     */
    static void stretch(PixelBuffer src, PixelBuffer dst, Appearance.Alpha alphaMode) {
        int frameH = Effects.frameHeight(dst);
        for (int y = 0; y < dst.height(); y++) {
            int fy = y % frameH;
            int sy = fy * src.height() / frameH;
            for (int x = 0; x < dst.width(); x++) {
                int sx = x * src.width() / dst.width();
                int s = src.getArgb(sx, sy);
                int shape = shapeAlpha(Colors.alpha(dst.getArgb(x, y)), alphaMode);
                dst.setArgb(x, y, Colors.withAlpha(s, Colors.alpha(s) * shape / 255));
            }
        }
    }
}
