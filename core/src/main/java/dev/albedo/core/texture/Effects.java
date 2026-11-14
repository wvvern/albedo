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
    static final double MUDDY_FROM = 70, MUDDY_TO = 125;
    /** The smallest frame width and height that get Bevel and Outline. */
    static final int MIN_LINE_FRAME = 4;
    /** The width of the Bevel band and of the Outline line, as a part of the frame width. */
    private static final float BEVEL_WIDTH = 3 / 16f, LINE_WIDTH = 1 / 16f;
    /** {@link #meanLightness} reads about this many samples across a frame. */
    private static final int SAMPLE_CELLS = 16;
    /** A tile darker than this OKLab lightness gets a lighter Outline. */
    private static final double DARK_TILE = 0.45;
    private static final double TAU = 2 * Math.PI;

    /** The target of Chroma in one frame: the OKLab chroma, the strength and the lightness limits. */
    private static final class ChromaTarget {
        final double chroma, strength, top, bottom;

        /** @param withHue false to move the colors only toward gray and the lightness limits */
        ChromaTarget(Chroma c, boolean withHue) {
            chroma = withHue ? MAX_CHROMA * c.saturation() / 100.0 : 0;
            strength = c.strength() / 100.0;
            double shift = (c.brightness() - Chroma.DEFAULT_BRIGHTNESS) / 50.0 * BRIGHTNESS_RANGE;
            top = Recolor.clamp(CHROMA_TOP + shift, 0, 1);
            bottom = Recolor.clamp(CHROMA_BOTTOM + shift, 0, 1);
        }

        /** Moves a color toward the target chroma at {@code hue} radians and into the lightness limits. */
        int color(int rgb, double hue) {
            double[] lab = Recolor.lab(rgb);
            double l = lab[0];
            if (l > top) l -= (l - top) * strength;
            else if (l < bottom) l += (bottom - l) * strength;
            double ta = chroma * Math.cos(hue), tb = chroma * Math.sin(hue);
            return Recolor.srgb(l, lab[1] + (ta - lab[1]) * strength, lab[2] + (tb - lab[2]) * strength);
        }
    }

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
     * @param tile whether the image is square block tiles (one per frame of an animation strip) that get Bevel
     * @param outline whether each frame gets Outline along the edge of its shape
     * @param gpuHue whether a GPU pass adds the hue of moving Chroma colors in each frame; a texture without it gets no Chroma,
     *     because its colors cannot move
     * @return true if the image was changed
     */
    static boolean apply(Appearance a, PixelBuffer img, String id, boolean tile, boolean outline, boolean gpuHue) {
        Chroma c = a.chroma();
        boolean chroma = c.isOn() && gpuHue;
        if (!a.changesPixels() || a.bevel() == 0 && a.outline() == 0 && !chroma || img.width() == 0 || img.height() == 0) return false;
        int w = img.width(), frameH = tile || outline ? frameHeight(img) : img.height();
        int frames = img.height() / frameH;
        // At Speed 0 the CPU paints the hue, because the pass adds none.
        ChromaTarget target = chroma ? new ChromaTarget(c, !a.chromaMoves()) : null;
        // Outline and Edge use one line for all frames, because the texels of the GPU pass are the same for all frames of a sprite.
        boolean edgeOn = chroma && c.edge();
        boolean lined = w >= MIN_LINE_FRAME && frameH >= MIN_LINE_FRAME;
        boolean[] line = outline && lined && (edgeOn || a.outline() > 0) ? edgeMask(img, id, frameH) : null;
        boolean[] edge = edgeOn ? line : null;
        // Each frame of a block animation gets the next part of the colors, which only the CPU can do for the same reason.
        for (int f = 0; f < frames; f++) {
            int y0 = f * frameH;
            if (chroma) chroma(img, y0, frameH, hueTurns(id), tile ? TAU * f / frames : 0, edge, target);
            if (!tile && !outline || !lined) continue;
            double tileL = meanLightness(img, y0, frameH);
            if (tile && a.bevel() > 0) bevel(img, y0, frameH, tileL, a.bevel() / 100.0);
            if (outline && a.outline() > 0) outline(img, line, y0, tileL, a.outline() / 100.0);
        }
        return true;
    }

    /**
     * Puts the {@link ChromaTexels} of one frame of {@code w} by {@code h} pixels into {@code out}, which tell a GPU pass
     * how much hue to add to each pixel that {@link #apply} painted with {@code gpuHue}.
     *
     * @param line the pixels of the {@link #edgeMask} of the sprite, or null when the frame has no line
     * @return false when the colors of the look do not move, which leaves {@code out} as it is
     */
    static boolean texels(Appearance a, String id, boolean[] line, int w, int h, int[] out) {
        if (!a.chromaMoves()) return false;
        Chroma c = a.chroma();
        double amount = MAX_CHROMA * c.saturation() / 100.0 * c.strength() / 100.0;
        // The outline halves the chroma of its pixels at full strength, so the hue that the pass adds there is smaller too.
        double onLine = amount * (1 - 0.5 * a.outline() / 100.0);
        double turns = hueTurns(id), hueTurn = hueAt(turns) / TAU;
        int inner = ChromaTexels.pack(c.speed(), hueTurn, amount);
        int edge = ChromaTexels.pack(c.speed(), hueTurn, onLine);
        boolean edgeOnly = c.edge() && line != null;
        for (int i = 0; i < w * h; i++) {
            // A texel of speed 0 keeps the color that the CPU painted.
            if (edgeOnly) out[i] = line[i] ? ChromaTexels.pack(c.speed(), edgeHue(turns, i % w, i / w, w, h) / TAU, onLine) : 0;
            else out[i] = line != null && line[i] ? edge : inner;
        }
        return true;
    }

    /** The height of one frame: a block animation is a strip of square frames, so its height is a multiple of its width. */
    static int frameHeight(PixelBuffer img) {
        int w = img.width(), h = img.height();
        return h > w && h % w == 0 ? w : h;
    }

    /**
     * A stable hue in radians for an id, the same for every part of one block on every version, and never in the
     * yellow-olive band that looks muddy at mid lightness.
     */
    static double hue(String id) {
        return hueAt(hueTurns(id));
    }

    /** The place of the hue of an id in the hues that Chroma uses, from 0 to 1. */
    private static double hueTurns(String id) {
        String s = hueKey(id);
        int h = 0x811C9DC5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        return ((h >>> 8) & 0xFFFF) / (double) 0x10000;
    }

    /** The hue in radians at a place in the hues that Chroma uses, which go around and skip the muddy band. */
    private static double hueAt(double turns) {
        return Math.toRadians(MUDDY_TO + (turns - Math.floor(turns)) * (360 - (MUDDY_TO - MUDDY_FROM)));
    }

    /** The Edge hue of a pixel: the hue of the id, turned by the angle of the pixel around the center of its frame. */
    private static double edgeHue(double turns, int x, int y, int w, int h) {
        return hueAt(turns + Math.atan2(y + 0.5 - h / 2.0, x + 0.5 - w / 2.0) / TAU);
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

    /**
     * Moves the colors of a frame toward {@code target} at the hue of {@code turns} turned by {@code offset} radians.
     *
     * @param edge null to change every pixel, or the pixels of the {@link #edgeMask} to change, each toward its {@link #edgeHue}
     */
    private static void chroma(PixelBuffer img, int y0, int frameH, double turns, double offset, boolean[] edge, ChromaTarget target) {
        double hue = hueAt(turns) + offset;
        // Pixel art repeats few colors, so each color is converted once per frame where the hue is the same for all pixels.
        Recolor.ColorCache cache = new Recolor.ColorCache(img.width() * frameH);
        int w = img.width();
        for (int y = y0; y < y0 + frameH; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getArgb(x, y);
                if (Colors.alpha(p) == 0) continue;
                int rgb = p | 0xFF000000, out;
                if (edge != null) {
                    if (!edge[(y - y0) * w + x]) continue;
                    out = target.color(rgb, edgeHue(turns, x, y - y0, w, frameH) + offset);
                } else {
                    int slot = cache.slot(rgb);
                    if (!cache.has(slot)) cache.store(slot, rgb, target.color(rgb, hue));
                    out = cache.value(slot, rgb);
                }
                img.setArgb(x, y, Colors.withAlpha(out, Colors.alpha(p)));
            }
        }
    }

    /** Shades a band inside the frame edges toward the side that contrasts with the tile, the same on every side. */
    private static void bevel(PixelBuffer img, int y0, int frameH, double tileL, double s) {
        int depth = Math.max(1, Math.round(img.width() * BEVEL_WIDTH));
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

    /** Moves the pixels of {@code line} in a frame toward a color that is darker than the tile, or lighter for a dark tile. */
    private static void outline(PixelBuffer img, boolean[] line, int y0, double tileL, double s) {
        double target = tileL < DARK_TILE ? Math.min(1, tileL + OUTLINE_CONTRAST) : Math.max(0, tileL - OUTLINE_CONTRAST);
        for (int i = 0; i < line.length; i++) {
            if (line[i]) shiftLightness(img, i % img.width(), y0 + i / img.width(), target, s);
        }
    }

    /**
     * The pixels that are on the Outline in any frame of {@code frameH} rows, by rows of one frame, so that one line
     * fits all frames of an animation whose shape changes.
     */
    static boolean[] edgeMask(PixelBuffer img, String id, int frameH) {
        boolean[] out = new boolean[img.width() * frameH];
        for (int y0 = 0; y0 + frameH <= img.height(); y0 += frameH) {
            boolean[] line = line(img, id, y0, frameH);
            for (int i = 0; i < out.length; i++) out[i] |= line[i];
        }
        return out;
    }

    /**
     * The pixels of the frame that starts at row {@code y0} that are on the Outline, by rows: those in the square of the
     * line width around an edge pixel (one outside the frame or a fully transparent one), which on an opaque frame is
     * the band along the frame border.
     */
    private static boolean[] line(PixelBuffer img, String id, int y0, int frameH) {
        int w = img.width(), n = w * frameH, width = Math.max(1, Math.round(w * LINE_WIDTH));
        boolean[] edge = new boolean[n], nearInRow = new boolean[n], out = new boolean[n];
        // An overlay lies on another texture of its block or item, so its transparent pixels are not the edge of a shape.
        if (!id.endsWith("_overlay")) {
            for (int i = 0; i < n; i++) edge[i] = Colors.alpha(img.getArgb(i % w, y0 + i / w)) == 0;
        }
        for (int y = 0; y < frameH; y++) {
            spread(edge, nearInRow, y * w, 1, w, width);
            spread(edge, nearInRow, y * w + w - 1, -1, w, width);
        }
        for (int x = 0; x < w; x++) {
            spread(nearInRow, out, x, w, frameH, width);
            spread(nearInRow, out, (frameH - 1) * w + x, -w, frameH, width);
        }
        return out;
    }

    /**
     * Marks the pixels of one row or column, {@code count} pixels from index {@code start} in steps of {@code step},
     * that are at most {@code width} pixels after its start or after a pixel of {@code from}, and the pixels of
     * {@code from}.
     */
    private static void spread(boolean[] from, boolean[] out, int start, int step, int count, int width) {
        int since = 0;
        for (int n = 0; n < count; n++) {
            int i = start + n * step;
            since = from[i] ? 0 : since + 1;
            if (since <= width) out[i] = true;
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
        int step = Math.max(1, img.width() / SAMPLE_CELLS);
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
