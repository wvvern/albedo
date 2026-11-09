package dev.albedo.core.texture;

import dev.albedo.core.Colors;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.config.Appearance;

import java.util.Arrays;

/**
 * Moves the colors of an image to a target color in OKLab, a space where equal steps look equal, so that the result
 * keeps the image's light and dark pattern and has the target as its average.
 */
final class Recolor {
    /** Color offsets larger than this, such as the specks of an ore, keep their color at any Colors setting. */
    private static final double ACCENT_FROM = 0.03, ACCENT_TO = 0.08;
    /** The lightness range near black and near white where a soft shoulder replaces a hard clip. */
    private static final double SHOULDER = 0.05;
    /** The least part of the light and dark spread that stays when the target is near black or white. */
    private static final double MIN_GAIN = 0.5;
    private static final double[] LINEAR = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            LINEAR[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
    }

    private Recolor() {}

    static void apply(Appearance a, PixelBuffer img) {
        Stats s = Stats.of(img);
        if (s == null) {
            PixelTransformer.fill(img, a.color(), a.alpha(), true);
            return;
        }
        double[] target = lab(a.color());
        double detail = a.detail() / 100.0, keepColors = a.keepColors() / 100.0;
        double centerL = target[0] + (s.mean[0] - target[0]) * a.keepLight() / 100.0;
        // Near black or white the full spread would clip, so the spread shrinks, and at its limit the center moves.
        double up = detail * s.spreadUp, down = detail * s.spreadDown, gain = 1;
        if (up > 0) gain = Math.min(gain, (1 - centerL) / up);
        if (down > 0) gain = Math.min(gain, centerL / down);
        if (gain < MIN_GAIN) {
            gain = MIN_GAIN;
            centerL = clamp(centerL, MIN_GAIN * down, 1 - MIN_GAIN * up);
        }
        ColorCache cache = new ColorCache(img.width() * img.height());
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) {
                int p = img.getArgb(x, y);
                int slot = cache.slot(p);
                if (!cache.has(slot)) {
                    double w = Colors.alpha(p) / 255.0; // a hole gets the target, a soft edge a part of its offset
                    double[] q = lab(p);
                    double da = q[1] - s.mean[1], db = q[2] - s.mean[2];
                    double colors = Math.max(keepColors, smoothstep(ACCENT_FROM, ACCENT_TO, Math.hypot(da, db))) * w;
                    double offset = gain * detail * w * (q[0] - s.mean[0]);
                    double l = offset == 0 ? clamp(centerL, 0, 1) : shoulder(centerL + offset);
                    int rgb = srgb(l, target[1] + colors * da, target[2] + colors * db);
                    cache.store(slot, p, Colors.withAlpha(rgb, PixelTransformer.shapedAlpha(Colors.alpha(p), a)));
                }
                img.setArgb(x, y, cache.value(slot, p));
            }
        }
    }

    /** The alpha-weighted OKLab mean of the visible pixels and the robust spreads of lightness above and below it. */
    private static final class Stats {
        final double[] mean;
        final double spreadUp, spreadDown;

        private Stats(double[] mean, double spreadUp, double spreadDown) {
            this.mean = mean;
            this.spreadUp = spreadUp;
            this.spreadDown = spreadDown;
        }

        /** One mean for the whole image, so that the frames of an animation strip keep their differences. */
        static Stats of(PixelBuffer img) {
            int step = PixelTransformer.sampleStep(img);
            double[] sum = new double[3];
            double weight = 0;
            int n = 0;
            double[] lightness = new double[(img.width() / step + 1) * (img.height() / step + 1)];
            for (int y = 0; y < img.height(); y += step) {
                for (int x = 0; x < img.width(); x += step) {
                    int p = img.getArgb(x, y);
                    int alpha = Colors.alpha(p);
                    if (alpha == 0) continue;
                    double[] q = lab(p);
                    for (int i = 0; i < 3; i++) sum[i] += q[i] * alpha;
                    weight += alpha;
                    if (alpha >= 128) lightness[n++] = q[0]; // faint pixels barely show, so they do not set the spread
                }
            }
            if (weight == 0) return null;
            double[] mean = {sum[0] / weight, sum[1] / weight, sum[2] / weight};
            double[] up = new double[n], down = new double[n];
            int ups = 0, downs = 0;
            for (int i = 0; i < n; i++) {
                double d = lightness[i] - mean[0];
                if (d > 0) up[ups++] = d;
                else if (d < 0) down[downs++] = -d;
            }
            return new Stats(mean, percentile95(up, ups), percentile95(down, downs));
        }

        private static double percentile95(double[] values, int count) {
            if (count == 0) return 0;
            Arrays.sort(values, 0, count);
            return values[Math.min(count - 1, (int) (count * 0.95))];
        }
    }

    /**
     * Colors seen in this image and their results, because textures have few distinct colors, with room for twice the
     * colors it keeps so that a lookup always reaches an empty slot soon.
     */
    private static final class ColorCache {
        private static final int MAX_SIZE = 1 << 13;
        private final int[] keys, values;
        private final boolean[] used;
        private final int mask;
        private int count;
        /** The result of the last color that did not fit, because the cache keeps at most half its size. */
        private int spare;

        ColorCache(int pixels) {
            int size = Integer.highestOneBit(Math.max(4, Math.min(MAX_SIZE, pixels * 2) - 1)) << 1;
            keys = new int[size];
            values = new int[size];
            used = new boolean[size];
            mask = size - 1;
        }

        /** The slot of the color, or the empty slot where it belongs. */
        int slot(int argb) {
            int i = (argb * 0x9E3779B9 >>> 16) & mask;
            while (used[i] && keys[i] != argb) i = (i + 1) & mask;
            return i;
        }

        boolean has(int slot) {
            return used[slot];
        }

        void store(int slot, int argb, int result) {
            if (count * 2 >= keys.length) {
                spare = result;
                return;
            }
            used[slot] = true;
            keys[slot] = argb;
            values[slot] = result;
            count++;
        }

        int value(int slot, int argb) {
            return used[slot] && keys[slot] == argb ? values[slot] : spare;
        }
    }

    /** OKLab {L, a, b} of an sRGB color. */
    static double[] lab(int argb) {
        double r = LINEAR[Colors.red(argb)], g = LINEAR[Colors.green(argb)], b = LINEAR[Colors.blue(argb)];
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[]{
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }

    /** The opaque sRGB color of an OKLab color, with less chroma at the same lightness and hue when it is out of gamut. */
    static int srgb(double lightness, double a, double b) {
        double[] rgb = linear(lightness, a, b);
        if (!inGamut(rgb)) {
            double lo = 0, hi = 1;
            for (int i = 0; i < 8; i++) {
                double mid = (lo + hi) / 2;
                if (inGamut(linear(lightness, a * mid, b * mid))) lo = mid;
                else hi = mid;
            }
            rgb = linear(lightness, a * lo, b * lo);
        }
        return Colors.argb(255, encode(rgb[0]), encode(rgb[1]), encode(rgb[2]));
    }

    private static double[] linear(double lightness, double a, double b) {
        double l = lightness + 0.3963377774 * a + 0.2158037573 * b;
        double m = lightness - 0.1055613458 * a - 0.0638541728 * b;
        double s = lightness - 0.0894841775 * a - 1.2914855480 * b;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        return new double[]{
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s};
    }

    private static boolean inGamut(double[] rgb) {
        for (double c : rgb) if (c < -1e-6 || c > 1 + 1e-6) return false;
        return true;
    }

    private static int encode(double linear) {
        double c = clamp(linear, 0, 1);
        double v = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
        return (int) Math.round(v * 255);
    }

    /** Lightness in [0, 1], where values past the shoulder near black and white bend instead of clipping. */
    private static double shoulder(double l) {
        if (l > 1 - SHOULDER) return 1 - SHOULDER * Math.exp((1 - SHOULDER - l) / SHOULDER);
        if (l < SHOULDER) return SHOULDER * Math.exp((l - SHOULDER) / SHOULDER);
        return l;
    }

    private static double smoothstep(double from, double to, double x) {
        double t = clamp((x - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
