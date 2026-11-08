package dev.singlecolor.core.config;

import java.util.Locale;

/** How a texture should look. Immutable. */
public final class Appearance {
    public enum Mode {
        /** Leave the texture alone. */
        ORIGINAL,
        /** Fill with {@link #color()}, keeping the texture's shape according to {@link #alpha()}. */
        SOLID,
        /** Fill with the texture's own average color: removes detail but keeps each block recognisable. */
        AVERAGE,
        /** Desaturate, keeping detail. */
        GRAYSCALE,
        /** Replace with a user image (stretched to fit), from the config's textures folder. */
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

    public static final Appearance ORIGINAL = new Appearance(Mode.ORIGINAL, 0xFFFFFFFF, null, Alpha.KEEP);

    private final Mode mode;
    private final int color;
    private final String texture;
    private final Alpha alpha;

    public Appearance(Mode mode, int color, String texture, Alpha alpha) {
        if (mode == Mode.TEXTURE && (texture == null || texture.isEmpty()))
            throw new IllegalArgumentException("mode 'texture' needs a \"texture\" file name");
        this.mode = mode;
        this.color = color;
        this.texture = texture;
        this.alpha = alpha;
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

    public boolean isOriginal() { return mode == Mode.ORIGINAL; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Appearance)) return false;
        Appearance a = (Appearance) o;
        return mode == a.mode && color == a.color && alpha == a.alpha
                && (texture == null ? a.texture == null : texture.equals(a.texture));
    }

    @Override
    public int hashCode() {
        return ((mode.hashCode() * 31 + color) * 31 + alpha.hashCode()) * 31 + (texture == null ? 0 : texture.hashCode());
    }

    @Override
    public String toString() {
        return "Appearance{" + mode.key() + ", #" + Integer.toHexString(color) + ", " + texture + ", " + alpha.key() + "}";
    }
}
