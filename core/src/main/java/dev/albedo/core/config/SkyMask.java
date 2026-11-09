package dev.albedo.core.config;

import java.util.Locale;

/** Where a sky layer shows: everywhere, or only where a lower layer (named by its id) is light or dark. */
public final class SkyMask {
    /** Which part of the mask layer lets this layer through. */
    public enum Mode {
        /** Where the mask layer is light and opaque. */
        LIGHT,
        /** Where the mask layer is dark and opaque. */
        DARK;

        public String key() { return name().toLowerCase(Locale.ROOT); }

        public static Mode byKey(String s) {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        }
    }

    public static final SkyMask NONE = new SkyMask(0, Mode.LIGHT);

    private final int layer;
    private final Mode mode;

    private SkyMask(int layer, Mode mode) {
        this.layer = layer;
        this.mode = mode;
    }

    /** A mask by the layer with id {@code layer}, which must be lower in the design. */
    public static SkyMask of(int layer, Mode mode) {
        if (layer <= 0) throw new IllegalArgumentException("a mask needs a layer id from 1");
        return new SkyMask(layer, mode);
    }

    public boolean isNone() { return layer == 0; }

    /** The id of the mask layer, or 0 for no mask. */
    public int layer() { return layer; }

    public Mode mode() { return mode; }

    public SkyMask withMode(Mode m) { return isNone() ? this : new SkyMask(layer, m); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SkyMask)) return false;
        SkyMask m = (SkyMask) o;
        return m.layer == layer && (layer == 0 || m.mode == mode);
    }

    @Override
    public int hashCode() { return layer == 0 ? 0 : layer * 31 + mode.ordinal(); }
}
