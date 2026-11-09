package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyLayer.Kind;
import dev.albedo.core.config.SkyLayer.Param;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.config.Transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The built-in custom skies, which the editor offers as starting points. */
public final class SkyPresets {
    /** An orange sunset in the west under a deep blue sky, with small stars only high in the sky, where it is dark. */
    public static final SkyDesign DUSK = new Builder()
            .add(Kind.GRADIENT, Param.COLOR_1, 0xFFF08A4B, Param.COLOR_2, 0xFF1A1F4D, Param.HEIGHT, 22, Param.AMOUNT, 45)
            .add(Kind.GLOW, Param.COLOR_1, 0xFFFF9E5E, Param.AMOUNT, 30, Param.HEIGHT, 22)
            .add(Kind.GRADIENT, Param.COLOR_1, 0xFFFFFFFF, Param.COLOR_2, 0xFF000000, Param.HEIGHT, 45, Param.AMOUNT, 15)
            .output(SkyLayer.Output.MASK_ONLY)
            .add(Kind.STARS, Param.AMOUNT, 25, Param.SEED, 3)
            .blend(SkyLayer.Blend.ADD).opacity(55).mask(3, SkyMask.Mode.DARK)
            .build(SkyDesign.Celestial.SHOW, SkyDesign.Light.DAY_NIGHT, SkyDesign.Extent.HORIZON);

    /** Pink and violet clouds of gas with stars, partly blurred and changed in hue through a noise mask. */
    public static final SkyDesign NEBULA = new Builder()
            .add(Kind.GRADIENT, Param.COLOR_1, 0xFF1A0F33, Param.COLOR_2, 0xFF05060F, Param.HEIGHT, 35, Param.AMOUNT, 70)
            .add(Kind.FRACTAL, Param.COLOR_1, 0xFF3B1C6E, Param.COLOR_2, 0xFFFF6FB0, Param.AMOUNT, 60, Param.SEED, 7)
            .blend(SkyLayer.Blend.ADD).opacity(90).place(new Transform(40, 10, 0, 130))
            .add(Kind.NOISE, Param.AMOUNT, 55, Param.SEED, 12).output(SkyLayer.Output.MASK_ONLY).place(new Transform(0, 0, 0, 80))
            .add(Kind.STARS, Param.AMOUNT, 70, Param.SEED, 5).blend(SkyLayer.Blend.ADD)
            .add(Kind.BLUR, Param.AMOUNT, 25).mask(3, SkyMask.Mode.LIGHT)
            .add(Kind.HUE_SHIFT, Param.AMOUNT, 12).mask(3, SkyMask.Mode.DARK)
            .add(Kind.BLOOM, Param.AMOUNT, 40, Param.HEIGHT, 55)
            .build(SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED, SkyDesign.Extent.SPHERE);

    /**
     * An active galaxy like Centaurus A high in the south-east: a tilted spiral disc with a dark lane of dust across its warm
     * bulge, and two blue jets from its black hole into turbulent lobes, over faint dust and stars.
     */
    public static final SkyDesign GALAXY = new Builder()
            .add(Kind.GRADIENT, Param.COLOR_1, 0xFF0A0D1A, Param.COLOR_2, 0xFF010206, Param.HEIGHT, 30, Param.AMOUNT, 60)
            .add(Kind.NOISE, Param.COLOR_1, 0xFF000000, Param.COLOR_2, 0xFF1C2238, Param.AMOUNT, 40, Param.SEED, 2)
            .blend(SkyLayer.Blend.ADD).opacity(45).place(new Transform(0, 0, 70, 200))
            .add(Kind.STARS, Param.AMOUNT, 60, Param.SEED, 9).blend(SkyLayer.Blend.ADD)
            .add(Kind.GALAXY, Param.COLOR_1, 0xFF8FB2FF, Param.COLOR_2, 0xFFFFD9A6, Param.COLOR_3, 0xFF6FC3FF, Param.AMOUNT, 40,
                    Param.TILT, 68, Param.DUST, 85, Param.JETS, 80, Param.SEED, 4)
            .blend(SkyLayer.Blend.ADD).place(new Transform(135, 46, 30, 140))
            .add(Kind.BLOOM, Param.AMOUNT, 35, Param.HEIGHT, 55)
            .build(SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED, SkyDesign.Extent.SPHERE);

    private static final Map<String, SkyDesign> ALL = new LinkedHashMap<String, SkyDesign>();

    static {
        ALL.put("dusk", DUSK);
        ALL.put("nebula", NEBULA);
        ALL.put("galaxy", GALAXY);
    }

    private SkyPresets() {}

    /** The presets by name, in the order the editor shows them. */
    public static Map<String, SkyDesign> all() {
        return Collections.unmodifiableMap(ALL);
    }

    /** The preset with this name in any case, or null. */
    public static SkyDesign byName(String name) {
        return ALL.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /** Puts layers together with ids from 1 in the order they are added. */
    private static final class Builder {
        private final List<SkyLayer> layers = new ArrayList<SkyLayer>();

        /** @param params pairs of a parameter and its value */
        Builder add(Kind kind, Object... params) {
            SkyLayer l = SkyLayer.of(layers.size() + 1, kind);
            for (int i = 0; i < params.length; i += 2) l = l.withParam((Param) params[i], (Integer) params[i + 1]);
            layers.add(l);
            return this;
        }

        private Builder change(SkyLayer next) {
            layers.set(layers.size() - 1, next);
            return this;
        }

        private SkyLayer last() { return layers.get(layers.size() - 1); }

        Builder blend(SkyLayer.Blend b) { return change(last().withBlend(b)); }

        Builder opacity(int o) { return change(last().withOpacity(o)); }

        Builder output(SkyLayer.Output o) { return change(last().withOutput(o)); }

        Builder mask(int id, SkyMask.Mode mode) { return change(last().withMask(SkyMask.of(id, mode))); }

        Builder place(Transform t) { return change(last().withTransform(t)); }

        SkyDesign build(SkyDesign.Celestial celestial, SkyDesign.Light light, SkyDesign.Extent extent) {
            return new SkyDesign(layers, celestial, light, extent);
        }
    }
}
