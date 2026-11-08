package dev.albedo.core.config;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** How a texture should look, as an immutable value. */
public final class Appearance {
    public enum Mode {
        ORIGINAL,
        /** Fill with {@link #color()}, keeping the texture's shape according to {@link #alpha()}. */
        SOLID,
        /** Move the texture's colors to {@link #color()} and keep its detail, tuned by the three recolor fields. */
        RECOLOR,
        /** Fill with the texture's own average color: removes detail but keeps each block recognisable. */
        AVERAGE,
        GRAYSCALE,
        /** Replace with a user image (stretched to fit) from the config's textures folder; falls back to the color. */
        TEXTURE,
        /** Fully transparent: the thing disappears (useful for clouds, weather, particles). */
        HIDDEN;

        public static Mode byKey(String s) {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        }

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    public enum Alpha {
        /** Keep each pixel's original alpha (glass stays see-through, leaves keep their holes). */
        KEEP,
        /** Pixels at or above half alpha become opaque, the rest fully transparent. */
        BINARY,
        /** Every pixel becomes opaque (leaves become solid cubes). */
        OPAQUE;

        public static Alpha byKey(String s) {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        }

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    /** The fields of a look, which a group sets for itself or takes from its category. */
    public enum Field { MODE, COLOR, TEXTURE, ALPHA, DETAIL, KEEP_COLORS, KEEP_LIGHT }

    public static final int DEFAULT_DETAIL = 100, DEFAULT_KEEP_COLORS = 0, DEFAULT_KEEP_LIGHT = 0;
    public static final Appearance ORIGINAL = new Appearance(Mode.ORIGINAL, 0xFFFFFFFF, null, Alpha.KEEP);

    private final Mode mode;
    private final int color;
    private final String texture;
    private final Alpha alpha;
    private final int detail, keepColors, keepLight;

    /** A look with the default recolor fields. */
    public Appearance(Mode mode, int color, String texture, Alpha alpha) {
        this(mode, color, texture, alpha, DEFAULT_DETAIL, DEFAULT_KEEP_COLORS, DEFAULT_KEEP_LIGHT);
    }

    /**
     * @param detail percent of the light and dark pattern that a recolor keeps
     * @param keepColors percent of the color differences between pixels that a recolor keeps
     * @param keepLight percent of the texture's own lightness that a recolor keeps instead of the color's lightness
     */
    public Appearance(Mode mode, int color, String texture, Alpha alpha, int detail, int keepColors, int keepLight) {
        this.mode = mode;
        this.color = color;
        this.texture = texture;
        this.alpha = alpha;
        this.detail = detail;
        this.keepColors = keepColors;
        this.keepLight = keepLight;
    }

    public static Appearance solid(int argb) {
        return new Appearance(Mode.SOLID, argb, null, Alpha.KEEP);
    }

    public static Appearance of(Mode mode) {
        return new Appearance(mode, 0xFFFFFFFF, null, Alpha.KEEP);
    }

    public Mode mode() { return mode; }
    public int color() { return color; }
    public String texture() { return texture; }
    public Alpha alpha() { return alpha; }
    public int detail() { return detail; }
    public int keepColors() { return keepColors; }
    public int keepLight() { return keepLight; }

    public boolean isOriginal() { return mode == Mode.ORIGINAL; }

    /** This look with the given fields taken from {@code from}. */
    public Appearance with(Set<Field> fields, Appearance from) {
        return new Appearance(
                fields.contains(Field.MODE) ? from.mode : mode,
                fields.contains(Field.COLOR) ? from.color : color,
                fields.contains(Field.TEXTURE) ? from.texture : texture,
                fields.contains(Field.ALPHA) ? from.alpha : alpha,
                fields.contains(Field.DETAIL) ? from.detail : detail,
                fields.contains(Field.KEEP_COLORS) ? from.keepColors : keepColors,
                fields.contains(Field.KEEP_LIGHT) ? from.keepLight : keepLight);
    }

    /** The fields in which this look differs from {@code other}. */
    public EnumSet<Field> differences(Appearance other) {
        EnumSet<Field> out = EnumSet.noneOf(Field.class);
        if (mode != other.mode) out.add(Field.MODE);
        if (color != other.color) out.add(Field.COLOR);
        if (!Objects.equals(texture, other.texture)) out.add(Field.TEXTURE);
        if (alpha != other.alpha) out.add(Field.ALPHA);
        if (detail != other.detail) out.add(Field.DETAIL);
        if (keepColors != other.keepColors) out.add(Field.KEEP_COLORS);
        if (keepLight != other.keepLight) out.add(Field.KEEP_LIGHT);
        return out;
    }

    public Appearance withMode(Mode m) { return new Appearance(m, color, texture, alpha, detail, keepColors, keepLight); }
    public Appearance withColor(int c) { return new Appearance(mode, c, texture, alpha, detail, keepColors, keepLight); }
    public Appearance withTexture(String t) { return new Appearance(mode, color, t, alpha, detail, keepColors, keepLight); }
    public Appearance withAlpha(Alpha a) { return new Appearance(mode, color, texture, a, detail, keepColors, keepLight); }

    public Appearance withRecolor(int newDetail, int newKeepColors, int newKeepLight) {
        return new Appearance(mode, color, texture, alpha, newDetail, newKeepColors, newKeepLight);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Appearance)) return false;
        Appearance a = (Appearance) o;
        return mode == a.mode && color == a.color && alpha == a.alpha && Objects.equals(texture, a.texture)
                && detail == a.detail && keepColors == a.keepColors && keepLight == a.keepLight;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, color, texture, alpha, detail, keepColors, keepLight);
    }
}
