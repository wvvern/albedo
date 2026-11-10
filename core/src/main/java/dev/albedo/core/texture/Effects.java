package dev.albedo.core.texture;

import dev.albedo.core.Colors;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.Chroma;

/** The effects of a look (Chroma, then Bevel, then Outline), which change colors after the style and never alpha. */
final class Effects {
    /** The OKLab chroma of a Chroma color at full strength and saturation 100, so that the default saturation 50 gives 0.13. */
    static final double MAX_CHROMA = 0.26;
    /** Lightness that Chroma pulls white down to and black up to at the default brightness, so that both can show a hue. */
    private static final double CHROMA_TOP = 0.78, CHROMA_BOTTOM = 0.3;
    /** How far brightness 0 and 100 move both lightness limits down and up. */
    private static final double BRIGHTNESS_RANGE = 0.2;
    /** Lightness change of Bevel at the edge, and of the Outline color from the tile, at full strength. */
    private static final double BEVEL_DEPTH = 0.22, OUTLINE_CONTRAST = 0.35;
    /** The OKLab hue band in degrees that Chroma skips, because yellow-olive looks muddy at mid lightness. */
    private static final double MUDDY_FROM = 70, MUDDY_TO = 125;

    /** Parts of a block that end its texture name and must not change its hue. */
    private static final String[] PART_SUFFIXES = {"_overlay", "_side", "_top", "_bottom", "_front", "_back", "_end", "_inner",
        "_outer", "_still", "_flow", "_flowing", "_particle", "_normal", "_on"};

    /** Legacy (1.8.9) block names whose modern name has a different base, as pairs of old and new name. */
    private static final String[][] LEGACY_NAMES = {{"grass", "grass_block"}, {"stonebrick", "stone_bricks"}, {"brick", "bricks"},
        {"cobblestone_mossy", "mossy_cobblestone"}, {"ice_packed", "packed_ice"}, {"tallgrass", "short_grass"},
        {"flower_rose", "poppy"}, {"pumpkin_face_off", "carved_pumpkin"}};

    /** Legacy prefixes that the modern name has as a suffix, for example {@code log_oak} and {@code oak_log}. */
    private static final String[][] LEGACY_PREFIXES = {{"log_", "_log"}, {"planks_", "_planks"}, {"wool_colored_", "_wool"},
        {"leaves_", "_leaves"}};

    private Effects() {}

    /**
     * @param id the texture id, which gives Chroma its hue
     * @param tile whether the image is square block tiles (one per frame of an animation strip) that get Bevel and Outline
     * @param gpuHue whether a GPU pass adds the hue of moving Chroma colors in each frame, so that the image gets the rest of Chroma only
     * @return true if the image was changed
     */
    static boolean apply(Appearance a, PixelBuffer img, String id, boolean tile, boolean gpuHue) {
        if (!a.hasEffects() || img.width() == 0 || img.height() == 0) return false;
        int w = img.width(), frameH = tile ? frameHeight(img) : img.height();
        int frames = img.height() / frameH;
        double hue = hue(id);
        Chroma c = a.chroma();
        boolean withHue = !gpuHue || !a.chromaMoves();
        // Each frame of a block animation gets the next part of the colors, which only the CPU can do, because the texels of
        // the GPU pass are the same for all frames of a sprite.
        for (int f = 0; f < frames; f++) {
            int y0 = f * frameH;
            if (c.isOn()) chroma(img, y0, frameH, hue + 2 * Math.PI * f / frames, c, withHue);
            if (!tile || w < 4 || frameH < 4) continue;
            double tileL = meanLightness(img, y0, frameH);
            if (a.bevel() > 0) bevel(img, y0, frameH, tileL, a.bevel() / 100.0);
            if (a.outline() > 0) outline(img, y0, frameH, tileL, a.outline() / 100.0);
        }
        return true;
    }

    /**
     * Puts the {@link ChromaTexels} of one frame of {@code w} by {@code h} pixels into {@code out}, which tell a GPU pass
     * how much hue to add to each pixel that {@link #apply} painted with {@code gpuHue}.
     *
     * @param tile whether the frame gets Bevel and Outline, as for {@link #apply}
     * @return false when the colors of the look do not move, which leaves {@code out} as it is
     */
    static boolean texels(Appearance a, String id, boolean tile, int w, int h, int[] out) {
        if (!a.chromaMoves()) return false;
        Chroma c = a.chroma();
        double amount = MAX_CHROMA * c.saturation() / 100.0 * c.strength() / 100.0;
        // The outline halves the chroma of the border pixels at full strength, so the hue that the pass adds there is smaller too.
        int border = tile && w >= 4 && h >= 4 && a.outline() > 0 ? Math.max(1, Math.round(w / 16f)) : 0;
        int inner = ChromaTexels.pack(c.speed(), hue(id) / (2 * Math.PI), amount);
        int edge = ChromaTexels.pack(c.speed(), hue(id) / (2 * Math.PI), amount * (1 - 0.5 * a.outline() / 100.0));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out[y * w + x] = edgeDistance(x, y, w, h) < border ? edge : inner;
        }
        return true;
    }

    /** The height of one frame: a block animation is a strip of square frames, so its height is a multiple of its width. */
    private static int frameHeight(PixelBuffer img) {
        int w = img.width(), h = img.height();
        return h > w && h % w == 0 ? w : h;
    }

    /**
     * A stable hue in radians for an id, the same for every part of one block on every version, and never in the
     * yellow-olive band that looks muddy at mid lightness.
     */
    static double hue(String id) {
        String s = hueKey(id);
        int h = 0x811C9DC5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        double t = ((h >>> 8) & 0xFFFF) / (double) 0x10000;
        return Math.toRadians(MUDDY_TO + t * (360 - (MUDDY_TO - MUDDY_FROM)));
    }

    /** The name that gives a texture its hue: no folder, frame number or part suffix, and the modern block name. */
    static String hueKey(String id) {
        String s = id == null ? "" : id;
        s = s.substring(s.indexOf(':') + 1);
        if (s.endsWith(".png")) s = s.substring(0, s.length() - 4);
        if (s.startsWith("textures/")) s = s.substring("textures/".length());
        if (s.startsWith("block/")) s = s.substring("block/".length());
        else if (s.startsWith("blocks/")) s = s.substring("blocks/".length());
        int end = s.length();
        while (end > 0 && Character.isDigit(s.charAt(end - 1))) end--;
        if (end < s.length() && end > 1 && s.charAt(end - 1) == '_') s = s.substring(0, end - 1);
        for (boolean cut = true; cut;) {
            cut = false;
            for (String suffix : PART_SUFFIXES) {
                if (s.length() > suffix.length() && s.endsWith(suffix)) {
                    s = s.substring(0, s.length() - suffix.length());
                    cut = true;
                }
            }
        }
        for (String[] p : LEGACY_PREFIXES) {
            if (s.startsWith(p[0]) && s.length() > p[0].length()) return s.substring(p[0].length()) + p[1];
        }
        for (String[] n : LEGACY_NAMES) {
            if (s.equals(n[0])) return n[1];
        }
        return s;
    }

    /** Moves the colors of a frame toward a hue, or with {@code withHue} false only toward gray and the lightness limits. */
    private static void chroma(PixelBuffer img, int y0, int frameH, double hue, Chroma c, boolean withHue) {
        double target = withHue ? MAX_CHROMA * c.saturation() / 100.0 : 0, s = c.strength() / 100.0;
        double ta = target * Math.cos(hue), tb = target * Math.sin(hue);
        double shift = (c.brightness() - Chroma.DEFAULT_BRIGHTNESS) / 50.0 * BRIGHTNESS_RANGE;
        double top = Recolor.clamp(CHROMA_TOP + shift, 0, 1), bottom = Recolor.clamp(CHROMA_BOTTOM + shift, 0, 1);
        // Pixel art repeats few colors, so each color is converted once per frame.
        Recolor.ColorCache cache = new Recolor.ColorCache(img.width() * frameH);
        for (int y = y0; y < y0 + frameH; y++) {
            for (int x = 0; x < img.width(); x++) {
                int p = img.getArgb(x, y);
                if (Colors.alpha(p) == 0) continue;
                int rgb = p | 0xFF000000, slot = cache.slot(rgb);
                if (!cache.has(slot)) cache.store(slot, rgb, chroma(rgb, ta, tb, s, top, bottom));
                img.setArgb(x, y, Colors.withAlpha(cache.value(slot, rgb), Colors.alpha(p)));
            }
        }
    }

    private static int chroma(int rgb, double ta, double tb, double s, double top, double bottom) {
        double[] lab = Recolor.lab(rgb);
        double l = lab[0];
        if (l > top) l -= (l - top) * s;
        else if (l < bottom) l += (bottom - l) * s;
        return Recolor.srgb(l, lab[1] + (ta - lab[1]) * s, lab[2] + (tb - lab[2]) * s);
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
