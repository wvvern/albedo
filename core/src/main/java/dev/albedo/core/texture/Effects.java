package dev.albedo.core.texture;

import dev.albedo.core.Colors;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.config.Appearance;

/** The edge effects of a look (Bevel, then Outline), which change colors after the style and never alpha. */
final class Effects {
    /** Lightness change of Bevel at the edge, and of the Outline color from the tile, at full strength. */
    private static final double BEVEL_DEPTH = 0.22, OUTLINE_CONTRAST = 0.35;

    private Effects() {}

    /**
     * @param tile whether the image is square block tiles (one per frame of an animation strip) that get Bevel and Outline
     * @return true if the image was changed
     */
    static boolean apply(Appearance a, PixelBuffer img, boolean tile) {
        if (!tile || !a.hasEffects() || img.width() == 0 || img.height() == 0) return false;
        int w = img.width(), frameH = frameHeight(img);
        if (w < 4 || frameH < 4) return false;
        for (int y0 = 0; y0 + frameH <= img.height(); y0 += frameH) {
            double tileL = meanLightness(img, y0, frameH);
            if (a.bevel() > 0) bevel(img, y0, frameH, tileL, a.bevel() / 100.0);
            if (a.outline() > 0) outline(img, y0, frameH, tileL, a.outline() / 100.0);
        }
        return true;
    }

    /** The height of one frame: a block animation is a strip of square frames, so its height is a multiple of its width. */
    private static int frameHeight(PixelBuffer img) {
        int w = img.width(), h = img.height();
        return h > w && h % w == 0 ? w : h;
    }

    /** Shades a band inside the frame edges toward the side that contrasts with the tile, the same on every side. */
    private static void bevel(PixelBuffer img, int y0, int frameH, double tileL, double s) {
        int depth = Math.max(1, Math.round(img.width() * 3 / 16f));
        double sign = tileL > 0.5 ? -1 : 1;
        for (int y = y0; y < y0 + frameH; y++) {
            for (int x = 0; x < img.width(); x++) {
                int e = edgeDistance(x, y - y0, img.width(), frameH);
                if (e >= depth) continue;
                double t = (double) (depth - e) / depth;
                shadeLightness(img, x, y, sign * BEVEL_DEPTH * s * t);
            }
        }
    }

    /** Moves the pixels on the frame border toward a color that is darker than the tile, or lighter for a dark tile. */
    private static void outline(PixelBuffer img, int y0, int frameH, double tileL, double s) {
        int width = Math.max(1, Math.round(img.width() / 16f));
        double target = tileL < 0.45 ? Math.min(1, tileL + OUTLINE_CONTRAST) : Math.max(0, tileL - OUTLINE_CONTRAST);
        for (int y = y0; y < y0 + frameH; y++) {
            for (int x = 0; x < img.width(); x++) {
                if (edgeDistance(x, y - y0, img.width(), frameH) >= width) continue;
                shiftLightness(img, x, y, target, s);
            }
        }
    }

    /** Moves a pixel's lightness toward {@code target} by {@code s} and halves its chroma at full strength. */
    private static void shiftLightness(PixelBuffer img, int x, int y, double target, double s) {
        int p = img.getArgb(x, y);
        if (Colors.alpha(p) == 0) return;
        double[] lab = Recolor.lab(p);
        double l = lab[0] + (target - lab[0]) * s, c = 1 - 0.5 * s;
        img.setArgb(x, y, Colors.withAlpha(Recolor.srgb(l, lab[1] * c, lab[2] * c), Colors.alpha(p)));
    }

    private static int edgeDistance(int x, int y, int w, int h) {
        return Math.min(Math.min(x, w - 1 - x), Math.min(y, h - 1 - y));
    }

    /** Adds {@code dl} to a pixel's lightness. */
    private static void shadeLightness(PixelBuffer img, int x, int y, double dl) {
        int p = img.getArgb(x, y);
        if (Colors.alpha(p) == 0) return;
        double[] lab = Recolor.lab(p);
        img.setArgb(x, y, Colors.withAlpha(Recolor.srgb(Recolor.clamp(lab[0] + dl, 0, 1), lab[1], lab[2]), Colors.alpha(p)));
    }

    /** The alpha-weighted mean OKLab lightness of a frame from a grid of samples, or 0.5 when it is fully transparent. */
    private static double meanLightness(PixelBuffer img, int y0, int frameH) {
        double sum = 0, weight = 0;
        int step = Math.max(1, img.width() / 16);
        for (int y = y0; y < y0 + frameH; y += step) {
            for (int x = 0; x < img.width(); x += step) {
                int p = img.getArgb(x, y);
                int alpha = Colors.alpha(p);
                if (alpha == 0) continue;
                sum += Recolor.lab(p)[0] * alpha;
                weight += alpha;
            }
        }
        return weight == 0 ? 0.5 : sum / weight;
    }
}
