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
        /** Fully transparent: the thing disappears (useful for clouds, weather, particles). */
        HIDDEN,
        /** A custom sky made of layers ({@link SkyDesign}), only for the sky and never given to a texture. */
        LAYERS;

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
    public enum Field { MODE, COLOR, ALPHA, DETAIL, KEEP_COLORS, KEEP_LIGHT, BEVEL, OUTLINE, CHROMA }

    public static final int DEFAULT_DETAIL = 100, DEFAULT_KEEP_COLORS = 0, DEFAULT_KEEP_LIGHT = 0;
    public static final Appearance ORIGINAL = new Appearance(Mode.ORIGINAL, 0xFFFFFFFF, Alpha.KEEP);

    private final Mode mode;
    private final int color;
    private final Alpha alpha;
    private final int detail, keepColors, keepLight;
    /** Edge effect strengths in percent, where 0 is off. */
    private final int bevel, outline;
    private final Chroma chroma;

    /** A look with the default recolor fields and no effects. */
    public Appearance(Mode mode, int color, Alpha alpha) {
        this(mode, color, alpha, DEFAULT_DETAIL, DEFAULT_KEEP_COLORS, DEFAULT_KEEP_LIGHT);
    }

    /**
     * @param detail percent of the light and dark pattern that a recolor keeps
     * @param keepColors percent of the color differences between pixels that a recolor keeps
     * @param keepLight percent of the texture's own lightness that a recolor keeps instead of the color's lightness
     */
    public Appearance(Mode mode, int color, Alpha alpha, int detail, int keepColors, int keepLight) {
        this(mode, color, alpha, detail, keepColors, keepLight, 0, 0, Chroma.OFF);
    }

    /**
     * @param bevel percent of the soft shade inside the edges of each block
     * @param outline percent of the line around each block
     * @param chroma the rainbow color that each kind of texture gets
     */
    public Appearance(Mode mode, int color, Alpha alpha, int detail, int keepColors, int keepLight,
                      int bevel, int outline, Chroma chroma) {
        this.mode = mode;
        this.color = color;
        this.alpha = alpha;
        this.detail = detail;
        this.keepColors = keepColors;
        this.keepLight = keepLight;
        this.bevel = bevel;
        this.outline = outline;
        this.chroma = chroma;
    }

    public static Appearance solid(int argb) {
        return new Appearance(Mode.SOLID, argb, Alpha.KEEP);
    }

    public static Appearance of(Mode mode) {
        return new Appearance(mode, 0xFFFFFFFF, Alpha.KEEP);
    }

    public Mode mode() { return mode; }
    public int color() { return color; }
    public Alpha alpha() { return alpha; }
    public int detail() { return detail; }
    public int keepColors() { return keepColors; }
    public int keepLight() { return keepLight; }
    public int bevel() { return bevel; }
    public int outline() { return outline; }
    public Chroma chroma() { return chroma; }

    public boolean isOriginal() { return mode == Mode.ORIGINAL; }

    /** Every texture style except Vanilla and Hide, the only styles that effects apply to. */
    public boolean changesPixels() { return mode != Mode.ORIGINAL && mode != Mode.HIDDEN && mode != Mode.LAYERS; }

    /** Whether an effect changes the texture: an effect is on and the style changes pixels. */
    public boolean hasEffects() { return changesPixels() && (bevel > 0 || outline > 0 || chroma.isOn()); }

    /** Whether the colors of this look go around over time. */
    public boolean chromaMoves() { return changesPixels() && chroma.moves(); }

    /** The Chroma step of this look at {@code timeMs}, which is 0 when its colors do not move. */
    public int chromaPhase(long timeMs) { return changesPixels() ? chroma.phase(timeMs) : 0; }

    /** This look with the given fields taken from {@code from}. */
    public Appearance with(Set<Field> fields, Appearance from) {
        return new Appearance(
                fields.contains(Field.MODE) ? from.mode : mode,
                fields.contains(Field.COLOR) ? from.color : color,
                fields.contains(Field.ALPHA) ? from.alpha : alpha,
                fields.contains(Field.DETAIL) ? from.detail : detail,
                fields.contains(Field.KEEP_COLORS) ? from.keepColors : keepColors,
                fields.contains(Field.KEEP_LIGHT) ? from.keepLight : keepLight,
                fields.contains(Field.BEVEL) ? from.bevel : bevel,
                fields.contains(Field.OUTLINE) ? from.outline : outline,
                fields.contains(Field.CHROMA) ? from.chroma : chroma);
    }

    /** The fields in which this look differs from {@code other}. */
    public EnumSet<Field> differences(Appearance other) {
        EnumSet<Field> out = EnumSet.noneOf(Field.class);
        if (mode != other.mode) out.add(Field.MODE);
        if (color != other.color) out.add(Field.COLOR);
        if (alpha != other.alpha) out.add(Field.ALPHA);
        if (detail != other.detail) out.add(Field.DETAIL);
        if (keepColors != other.keepColors) out.add(Field.KEEP_COLORS);
        if (keepLight != other.keepLight) out.add(Field.KEEP_LIGHT);
        if (bevel != other.bevel) out.add(Field.BEVEL);
        if (outline != other.outline) out.add(Field.OUTLINE);
        if (!chroma.equals(other.chroma)) out.add(Field.CHROMA);
        return out;
    }

    public Appearance withMode(Mode m) { return new Appearance(m, color, alpha, detail, keepColors, keepLight, bevel, outline, chroma); }
    public Appearance withColor(int c) { return new Appearance(mode, c, alpha, detail, keepColors, keepLight, bevel, outline, chroma); }
    public Appearance withAlpha(Alpha a) { return new Appearance(mode, color, a, detail, keepColors, keepLight, bevel, outline, chroma); }

    public Appearance withRecolor(int newDetail, int newKeepColors, int newKeepLight) {
        return new Appearance(mode, color, alpha, newDetail, newKeepColors, newKeepLight, bevel, outline, chroma);
    }

    public Appearance withEffects(int newBevel, int newOutline, Chroma newChroma) {
        return new Appearance(mode, color, alpha, detail, keepColors, keepLight, newBevel, newOutline, newChroma);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Appearance)) return false;
        Appearance a = (Appearance) o;
        return mode == a.mode && color == a.color && alpha == a.alpha
                && detail == a.detail && keepColors == a.keepColors && keepLight == a.keepLight
                && bevel == a.bevel && outline == a.outline && chroma.equals(a.chroma);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, color, alpha, detail, keepColors, keepLight, bevel, outline, chroma);
    }
}
