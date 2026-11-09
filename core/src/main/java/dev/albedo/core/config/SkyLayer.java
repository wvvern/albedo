package dev.albedo.core.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One immutable layer of a custom sky: a generator that paints, or a filter that changes the layers below it, where
 * each layer blends over the result of the lower layers with its opacity times its mask.
 */
public final class SkyLayer {
    /** What a kind of layer does with the layers below it. */
    public enum Role {
        /** Paints its own image from nothing. */
        GENERATOR,
        /** Changes the image of the layers below it. */
        FILTER
    }

    /** The settings of a layer besides its place, which each kind uses some of. */
    public enum Param {
        COLOR_1("color1", 0, 0),
        COLOR_2("color2", 0, 0),
        /** A percent whose meaning depends on the kind (radius, levels, coverage, strength). */
        AMOUNT("amount", 0, 100),
        /** A percent of height above the horizon, or a threshold. */
        HEIGHT("height", 0, 100),
        SEED("seed", 0, 99999);

        public final String key;
        private final int min, max;

        Param(String key, int min, int max) {
            this.key = key;
            this.min = min;
            this.max = max;
        }

        public boolean isColor() { return this == COLOR_1 || this == COLOR_2; }

        public int min() { return min; }

        public int max() { return max; }

        /** The value kept inside its range; colors are not changed. */
        public int clamp(int value) {
            return isColor() ? value : Math.max(min, Math.min(max, value));
        }
    }

    /** The kinds of layers, each with its role, its parameters and their defaults, and the axes of its place. */
    public enum Kind {
        GRADIENT(Role.GENERATOR, EnumSet.of(Transform.Axis.PITCH, Transform.Axis.ROLL), Transform.IDENTITY,
                Param.COLOR_1, 0xFFF08A4B, Param.COLOR_2, 0xFF0B1026, Param.HEIGHT, 30, Param.AMOUNT, 60),
        GLOW(Role.GENERATOR, EnumSet.of(Transform.Axis.YAW, Transform.Axis.PITCH, Transform.Axis.ROLL), new Transform(270, 0, 0, 100),
                Param.COLOR_1, 0xFFFFB36B, Param.AMOUNT, 40, Param.HEIGHT, 25),
        NOISE(Role.GENERATOR, EnumSet.allOf(Transform.Axis.class), Transform.IDENTITY,
                Param.COLOR_1, 0xFF1B1F3A, Param.COLOR_2, 0xFFB0C4FF, Param.AMOUNT, 60, Param.SEED, 1),
        FRACTAL(Role.GENERATOR, EnumSet.allOf(Transform.Axis.class), Transform.IDENTITY,
                Param.COLOR_1, 0xFF5B2A86, Param.COLOR_2, 0xFFFF7AB6, Param.AMOUNT, 50, Param.SEED, 1),
        GALAXY(Role.GENERATOR, EnumSet.allOf(Transform.Axis.class), new Transform(135, 45, 20, 100),
                Param.COLOR_1, 0xFF9FB4FF, Param.COLOR_2, 0xFFFFE7B0, Param.AMOUNT, 50, Param.SEED, 1),
        STARS(Role.GENERATOR, EnumSet.allOf(Transform.Axis.class), Transform.IDENTITY,
                Param.COLOR_1, 0xFFFFFFFF, Param.AMOUNT, 50, Param.HEIGHT, 50, Param.SEED, 1),
        BLUR(Role.FILTER, EnumSet.noneOf(Transform.Axis.class), Transform.IDENTITY, Param.AMOUNT, 30),
        CRYSTALLIZE(Role.FILTER, EnumSet.allOf(Transform.Axis.class), Transform.IDENTITY, Param.SEED, 1),
        PIXELATE(Role.FILTER, EnumSet.allOf(Transform.Axis.class), Transform.IDENTITY),
        POSTERIZE(Role.FILTER, EnumSet.noneOf(Transform.Axis.class), Transform.IDENTITY, Param.AMOUNT, 30),
        HUE_SHIFT(Role.FILTER, EnumSet.noneOf(Transform.Axis.class), Transform.IDENTITY, Param.AMOUNT, 25),
        BLOOM(Role.FILTER, EnumSet.noneOf(Transform.Axis.class), Transform.IDENTITY, Param.AMOUNT, 50, Param.HEIGHT, 60),
        SWIRL(Role.FILTER, EnumSet.allOf(Transform.Axis.class), new Transform(0, 60, 0, 100), Param.AMOUNT, 50),
        VIGNETTE(Role.FILTER, EnumSet.allOf(Transform.Axis.class), new Transform(0, 90, 0, 100),
                Param.COLOR_1, 0xFF000000, Param.AMOUNT, 60);

        private final Role role;
        private final Set<Transform.Axis> axes;
        private final Transform defaultTransform;
        private final Map<Param, Integer> defaults = new EnumMap<Param, Integer>(Param.class);
        private final List<Param> params;

        /** @param defaults pairs of a parameter and its default value, in the order the editor shows them */
        Kind(Role role, Set<Transform.Axis> axes, Transform defaultTransform, Object... defaults) {
            this.role = role;
            this.axes = Collections.unmodifiableSet(axes);
            this.defaultTransform = defaultTransform;
            Param[] order = new Param[defaults.length / 2];
            for (int i = 0; i < defaults.length; i += 2) {
                order[i / 2] = (Param) defaults[i];
                this.defaults.put((Param) defaults[i], (Integer) defaults[i + 1]);
            }
            this.params = Collections.unmodifiableList(Arrays.asList(order));
        }

        public Role role() { return role; }

        /** The parameters this kind uses, in the order the editor shows them. */
        public List<Param> params() { return params; }

        public boolean uses(Param p) { return defaults.containsKey(p); }

        public int defaultValue(Param p) {
            Integer v = defaults.get(p);
            return v == null ? 0 : v;
        }

        /** The axes of the place that change this kind; the others have no effect. */
        public Set<Transform.Axis> axes() { return axes; }

        /** The place a new layer of this kind gets. */
        public Transform defaultTransform() { return defaultTransform; }

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    /** How a layer mixes with the layers below it, in linear light. */
    public enum Blend {
        NORMAL, ADD, SCREEN, MULTIPLY;

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Whether a layer shows in the sky or only serves as a mask for higher layers. */
    public enum Output {
        SHOW,
        /** Made, but not mixed into the sky: only higher layers use it as their mask. */
        MASK_ONLY;

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    private final int id;
    private final Kind kind;
    private final Blend blend;
    private final int opacity;
    private final Output output;
    private final SkyMask mask;
    /** One value for each parameter of the kind and no other. */
    private final Map<Param, Integer> params;
    private final Transform transform;

    /**
     * @param opacity percent from 0 to 100
     * @param params values for some parameters of the kind; the others take their defaults and values for parameters
     *               the kind does not use are dropped
     */
    public SkyLayer(int id, Kind kind, Blend blend, int opacity, Output output, SkyMask mask, Map<Param, Integer> params,
                    Transform transform) {
        this.id = id;
        this.kind = kind;
        this.blend = blend;
        this.opacity = Math.max(0, Math.min(100, opacity));
        this.output = output;
        this.mask = mask;
        EnumMap<Param, Integer> all = new EnumMap<Param, Integer>(Param.class);
        for (Param p : kind.params()) all.put(p, params.containsKey(p) ? p.clamp(params.get(p)) : kind.defaultValue(p));
        this.params = Collections.unmodifiableMap(all);
        this.transform = transform;
    }

    /** A new layer of {@code kind} with its defaults. */
    public static SkyLayer of(int id, Kind kind) {
        return new SkyLayer(id, kind, Blend.NORMAL, 100, Output.SHOW, SkyMask.NONE, Collections.<Param, Integer>emptyMap(),
                kind.defaultTransform());
    }

    public int id() { return id; }
    public Kind kind() { return kind; }
    public Blend blend() { return blend; }
    public int opacity() { return opacity; }
    public Output output() { return output; }
    public SkyMask mask() { return mask; }
    public Transform transform() { return transform; }

    /** The value of a parameter of the kind, or 0 for one it does not use. */
    public int param(Param p) {
        Integer v = params.get(p);
        return v == null ? 0 : v;
    }

    public Map<Param, Integer> params() { return params; }

    public SkyLayer withBlend(Blend b) { return new SkyLayer(id, kind, b, opacity, output, mask, params, transform); }
    public SkyLayer withOpacity(int o) { return new SkyLayer(id, kind, blend, o, output, mask, params, transform); }
    public SkyLayer withOutput(Output o) { return new SkyLayer(id, kind, blend, opacity, o, mask, params, transform); }
    public SkyLayer withMask(SkyMask m) { return new SkyLayer(id, kind, blend, opacity, output, m, params, transform); }
    public SkyLayer withTransform(Transform t) { return new SkyLayer(id, kind, blend, opacity, output, mask, params, t); }

    public SkyLayer withParam(Param p, int value) {
        EnumMap<Param, Integer> next = new EnumMap<Param, Integer>(params);
        next.put(p, value);
        return new SkyLayer(id, kind, blend, opacity, output, mask, next, transform);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SkyLayer)) return false;
        SkyLayer l = (SkyLayer) o;
        return l.id == id && l.kind == kind && l.blend == blend && l.opacity == opacity && l.output == output
                && l.mask.equals(mask) && l.params.equals(params) && l.transform.equals(transform);
    }

    @Override
    public int hashCode() {
        return ((((id * 31 + kind.hashCode()) * 31 + blend.hashCode()) * 31 + opacity) * 31 + mask.hashCode()) * 31
                + params.hashCode() * 7 + transform.hashCode() + output.ordinal();
    }
}
