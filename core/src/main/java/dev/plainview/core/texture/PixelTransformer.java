package dev.plainview.core.texture;

import dev.plainview.core.Colors;
import dev.plainview.core.PixelBuffer;
import dev.plainview.core.config.Appearance;

/** Applies an {@link Appearance} to an image in place. Pure function of its inputs. */
public final class PixelTransformer {
    /** Supplies user images for {@link Appearance.Mode#TEXTURE}. May return null when the file is missing. */
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

    /**
     * Fills every pixel with {@code color}. When {@code respectShape} is set, the output alpha follows the source
     * pixel's alpha per {@code alphaMode} (multiplied by the color's own alpha).
     */
    static void fill(PixelBuffer img, int color, Appearance.Alpha alphaMode, boolean respectShape) {
        int colorAlpha = Colors.alpha(color);
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) {
                int a = respectShape ? shapeAlpha(Colors.alpha(img.getArgb(x, y)), alphaMode) : 255;
                img.setArgb(x, y, Colors.withAlpha(color, a * colorAlpha / 255));
            }
        }
    }

    static int shapeAlpha(int srcAlpha, Appearance.Alpha mode) {
        switch (mode) {
            case OPAQUE: return 255;
            case BINARY: return srcAlpha >= 128 ? 255 : 0;
            default: return srcAlpha;
        }
    }

    /**
     * Alpha-weighted average of the visible pixels; opaque result. Fully transparent images give mid gray.
     * Large images are sampled on a grid of at most about 64 x 64 pixels, which is plenty for one color.
     */
    public static int average(PixelBuffer img) {
        long r = 0, g = 0, b = 0, w = 0;
        int step = Math.max(1, Math.max(img.width(), img.height()) / 64);
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
     * Nearest-neighbour stretch of {@code src} over {@code dst}. Tall animation strips (height a multiple of width)
     * get the image once per square frame so every frame looks the same.
     */
    static void stretch(PixelBuffer src, PixelBuffer dst, Appearance.Alpha alphaMode) {
        int frameH = dst.height();
        if (dst.height() > dst.width() && dst.height() % dst.width() == 0) frameH = dst.width();
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
