package dev.albedo.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Color parsing/formatting and channel helpers for 0xAARRGGBB ints. */
public final class Colors {
    private static final Map<String, Integer> NAMED = new HashMap<String, Integer>();

    static {
        NAMED.put("white", 0xFFFFFFFF);
        NAMED.put("black", 0xFF000000);
        NAMED.put("gray", 0xFF808080);
        NAMED.put("grey", 0xFF808080);
        NAMED.put("lightgray", 0xFFC0C0C0);
        NAMED.put("darkgray", 0xFF404040);
        NAMED.put("red", 0xFFFF0000);
        NAMED.put("green", 0xFF00FF00);
        NAMED.put("blue", 0xFF0000FF);
        NAMED.put("transparent", 0x00000000);
    }

    private Colors() {}

    public static int alpha(int argb) { return argb >>> 24; }
    public static int red(int argb) { return (argb >> 16) & 0xFF; }
    public static int green(int argb) { return (argb >> 8) & 0xFF; }
    public static int blue(int argb) { return argb & 0xFF; }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int withAlpha(int argb, int alpha) {
        return (clamp(alpha) << 24) | (argb & 0x00FFFFFF);
    }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    /**
     * Parses "#RGB", "#RRGGBB", "#AARRGGBB", "0x..." or a color name, where missing alpha means opaque.
     *
     * @throws IllegalArgumentException for anything else
     */
    public static int parse(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT);
        Integer named = NAMED.get(s);
        if (named != null) return named;
        if (s.startsWith("#")) s = s.substring(1);
        else if (s.startsWith("0x")) s = s.substring(2);
        try {
            if (s.length() == 3) {
                int r = Integer.parseInt(s.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(s.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(s.substring(2, 3), 16) * 17;
                return argb(255, r, g, b);
            }
            if (s.length() == 6) return 0xFF000000 | Integer.parseInt(s, 16);
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Not a color: '" + text + "' (use #RGB, #RRGGBB, #AARRGGBB or a name)");
    }

    public static String format(int argb) {
        if (alpha(argb) == 255) return String.format("#%06X", argb & 0xFFFFFF);
        return String.format("#%08X", argb);
    }

    /** The opaque color of hue, saturation and value in [0, 1], with a hue that wraps. */
    public static int fromHsv(float h, float s, float v) {
        float r, g, b;
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        switch (i) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q;
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /** @return {hue, saturation, value} in [0, 1], from the RGB channels only */
    public static float[] toHsv(int argb) {
        float r = red(argb) / 255f, g = green(argb) / 255f, b = blue(argb) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) / 6f;
            else if (max == g) h = ((b - r) / d + 2) / 6f;
            else h = ((r - g) / d + 4) / 6f;
            if (h < 0) h += 1;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    /** Converts between 0xAARRGGBB and the 0xAABBGGRR layout some image classes use (the swap is its own inverse). */
    public static int swapRedBlue(int color) {
        return (color & 0xFF00FF00) | ((color & 0xFF) << 16) | ((color >> 16) & 0xFF);
    }
}
