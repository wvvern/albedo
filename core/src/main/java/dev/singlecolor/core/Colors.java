package dev.singlecolor.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Color parsing/formatting and channel helpers. All colors are 0xAARRGGBB ints. */
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

    public static int rgb(int argb) { return argb & 0x00FFFFFF; }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    /**
     * Parses "#RGB", "#RRGGBB", "#AARRGGBB", "0x..." or a color name. Missing alpha means opaque.
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
            // fall through
        }
        throw new IllegalArgumentException("Not a color: '" + text + "' (use #RRGGBB, #AARRGGBB or a name)");
    }

    public static String format(int argb) {
        if (alpha(argb) == 255) return String.format("#%06X", argb & 0xFFFFFF);
        return String.format("#%08X", argb);
    }

    /** Converts 0xAARRGGBB to the 0xAABBGGRR layout used by some image classes. */
    public static int argbToAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
    }

    /** Inverse of {@link #argbToAbgr}; the swap is symmetric. */
    public static int abgrToArgb(int abgr) {
        return argbToAbgr(abgr);
    }
}
