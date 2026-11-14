package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyLayer.Kind;
import dev.albedo.core.config.SkyLayer.Param;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.config.Transform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** The built-in custom skies, which the editor offers as starting points. */
public final class SkyPresets {
    /**
     * An orange sunset in the west under a deep blue sky, with small stars only high in the sky, where it is dark, which
     * stands still, because a turn would carry the sunset and the orange below the horizon up into the sky.
     */
    public static final SkyDesign DUSK = new Builder()
            .add(Kind.GRADIENT).param(Param.COLOR_1, 0xFFF08A4B).param(Param.COLOR_2, 0xFF1A1F4D).param(Param.HEIGHT, 22)
                    .param(Param.AMOUNT, 45)
            .add(Kind.GLOW).param(Param.COLOR_1, 0xFFFF9E5E).param(Param.AMOUNT, 30).param(Param.HEIGHT, 22)
            .add(Kind.GRADIENT).param(Param.COLOR_1, 0xFFFFFFFF).param(Param.COLOR_2, 0xFF000000).param(Param.HEIGHT, 45)
                    .param(Param.AMOUNT, 15)
            .output(SkyLayer.Output.MASK_ONLY)
            .add(Kind.STARS).param(Param.AMOUNT, 40).param(Param.SEED, 3)
            .blend(SkyLayer.Blend.ADD).opacity(75).mask(3, SkyMask.Mode.DARK)
            .build(SkyDesign.Celestial.SHOW, SkyDesign.Light.DAY_NIGHT, SkyDesign.Extent.SPHERE)
            .withTurning(SkyTurning.STILL);

    /**
     * Pink and violet clouds of gas that slowly pulse, partly blurred and changed in hue through a noise mask, with sharp stars
     * over them, where the blurred part and the bloom come from the bake at time 0, so only the sharp part pulses.
     */
    public static final SkyDesign NEBULA = new Builder()
            .add(Kind.GRADIENT).param(Param.COLOR_1, 0xFF1A0F33).param(Param.COLOR_2, 0xFF05060F).param(Param.HEIGHT, 35)
                    .param(Param.AMOUNT, 70)
            .add(Kind.FRACTAL).param(Param.COLOR_1, 0xFF3B1C6E).param(Param.COLOR_2, 0xFFFF6FB0).param(Param.AMOUNT, 60)
                    .param(Param.PULSE, 50).param(Param.SEED, 7)
            .blend(SkyLayer.Blend.ADD).opacity(90).place(new Transform(40, 10, 0, 130))
            .add(Kind.NOISE).param(Param.AMOUNT, 55).param(Param.SEED, 12).output(SkyLayer.Output.MASK_ONLY)
                    .place(new Transform(0, 0, 0, 80))
            .add(Kind.BLUR).param(Param.AMOUNT, 25).mask(3, SkyMask.Mode.LIGHT)
            .add(Kind.HUE_SHIFT).param(Param.AMOUNT, 12).mask(3, SkyMask.Mode.DARK)
            .add(Kind.STARS).param(Param.AMOUNT, 70).param(Param.TWINKLE, 50).param(Param.SEED, 5).blend(SkyLayer.Blend.ADD)
            .add(Kind.BLOOM).param(Param.AMOUNT, 40).param(Param.HEIGHT, 55)
            .build(SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED, SkyDesign.Extent.SPHERE);

    /**
     * An active galaxy like Centaurus A high in the south-east: a tilted disc in a large warm halo with threads of dust
     * across its center, and two blue jets from its black hole into red lobes with rings, which move slowly, over far
     * galaxies, a cloud of the stars of our own galaxy, faint wisps of gas and stars with long spikes.
     */
    public static final SkyDesign GALAXY = new Builder()
            .add(Kind.GRADIENT).param(Param.COLOR_1, 0xFF0A0D1A).param(Param.COLOR_2, 0xFF010206).param(Param.HEIGHT, 30)
                    .param(Param.AMOUNT, 60)
            .add(Kind.NOISE).param(Param.COLOR_1, 0xFF000000).param(Param.COLOR_2, 0xFF1C2238).param(Param.AMOUNT, 40).param(Param.SEED, 2)
            .blend(SkyLayer.Blend.ADD).opacity(45).place(new Transform(0, 0, 70, 200))
            .add(Kind.STARS).param(Param.AMOUNT, 60).param(Param.HEIGHT, 85).param(Param.TWINKLE, 50).param(Param.SEED, 9)
                    .blend(SkyLayer.Blend.ADD)
            .add(Kind.GALAXY).param(Param.COLOR_1, 0xFF8FB2FF).param(Param.COLOR_2, 0xFFFFD9A6).param(Param.COLOR_3, 0xFF6FC3FF)
                    .param(Param.AMOUNT, 40).param(Param.TILT, 68).param(Param.DUST, 85).param(Param.JETS, 80).param(Param.DETAIL, 70)
                    .param(Param.MOTION, 50).param(Param.SEED, 4)
            .blend(SkyLayer.Blend.ADD).place(new Transform(135, 46, 30, 140))
            .add(Kind.DEEP_FIELD).param(Param.AMOUNT, 55).param(Param.HEIGHT, 45).param(Param.SEED, 3)
            .blend(SkyLayer.Blend.ADD).place(new Transform(195, 22, 55, 100))
            .add(Kind.FRACTAL).param(Param.COLOR_1, 0xFF1A2A52).param(Param.COLOR_2, 0xFF8A4A3C).param(Param.AMOUNT, 75)
                    .param(Param.SEED, 21)
            .blend(SkyLayer.Blend.ADD).opacity(16).place(new Transform(0, 0, 0, 260))
            .add(Kind.BLOOM).param(Param.AMOUNT, 35).param(Param.HEIGHT, 55)
            .build(SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED, SkyDesign.Extent.SPHERE);

    /** A clear dark night with stars, a faint band of dust and a shower of shooting stars from the north-east. */
    public static final SkyDesign METEORS = new Builder()
            .add(Kind.GRADIENT).param(Param.COLOR_1, 0xFF101A33).param(Param.COLOR_2, 0xFF02040A).param(Param.HEIGHT, 25)
                    .param(Param.AMOUNT, 60)
            .add(Kind.NOISE).param(Param.COLOR_1, 0xFF000000).param(Param.COLOR_2, 0xFF26304D).param(Param.AMOUNT, 40).param(Param.SEED, 6)
            .blend(SkyLayer.Blend.ADD).opacity(45).place(new Transform(0, 0, 60, 200))
            .add(Kind.STARS).param(Param.AMOUNT, 55).param(Param.TWINKLE, 50).param(Param.SEED, 11).blend(SkyLayer.Blend.ADD)
            .add(Kind.METEORS).param(Param.SHOWER, 70).blend(SkyLayer.Blend.ADD)
            .build(SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED, SkyDesign.Extent.SPHERE);

    /**
     * Folded green curtains of the northern light over the north, with red tops, over an open night sky with twinkling stars,
     * which stands still, because a turn would carry the green glow along the horizon up across the sky.
     */
    public static final SkyDesign AURORA = new Builder()
            .add(Kind.GRADIENT).param(Param.COLOR_1, 0xFF0A1424).param(Param.COLOR_2, 0xFF020409).param(Param.HEIGHT, 22)
                    .param(Param.AMOUNT, 55)
            .add(Kind.GLOW).param(Param.COLOR_1, 0xFF1C5C40).param(Param.AMOUNT, 35).param(Param.HEIGHT, 6)
            .blend(SkyLayer.Blend.ADD).opacity(6).place(new Transform(0, 0, 0, 100))
            .add(Kind.STARS).param(Param.AMOUNT, 60).param(Param.TWINKLE, 50).param(Param.SEED, 6).blend(SkyLayer.Blend.ADD).opacity(85)
            .add(Kind.AURORA).param(Param.COLOR_2, 0xFF7A4FE0).param(Param.AMOUNT, 65).param(Param.SEED, 3).blend(SkyLayer.Blend.ADD)
            .build(SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED, SkyDesign.Extent.SPHERE)
            .withTurning(SkyTurning.STILL);

    /** A long exposure of a dark clear night: the stars turn around the pole in the north and draw arcs behind them. */
    public static final SkyDesign TRAILS = new Builder()
            .add(Kind.GRADIENT).param(Param.COLOR_1, 0xFF14203A).param(Param.COLOR_2, 0xFF020308).param(Param.HEIGHT, 20)
                    .param(Param.AMOUNT, 55)
            .add(Kind.STARS).param(Param.AMOUNT, 75).param(Param.HEIGHT, 30).param(Param.SEED, 8).blend(SkyLayer.Blend.ADD)
            .build(SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED, SkyDesign.Extent.SPHERE)
            .withTurning(new SkyTurning(100, 1, SkyTurning.Clock.REAL, 40, SkyTurning.Direction.NORMAL, 0, 45, 60));

    private static final List<Preset> ALL = Collections.unmodifiableList(Arrays.asList(
            new Preset("dusk", "Dusk", "An orange sunset with the first stars", 270, DUSK),
            new Preset("nebula", "Nebula", "Pink and violet clouds that slowly pulse", 180, NEBULA),
            new Preset("galaxy", "Galaxy", "A galaxy with two blue jets over the stars", 135, GALAXY),
            new Preset("meteors", "Meteors", "A shower of shooting stars on a clear night", 45, METEORS),
            new Preset("aurora", "Aurora", "Green northern lights over a starry night", 0, AURORA),
            new Preset("trails", "Trails", "Stars that turn and draw long arcs", 0, TRAILS)));

    private SkyPresets() {}

    /** A built-in sky with the texts and the view of its card in the editor. */
    public static final class Preset {
        private final String key, name, description;
        private final int facing;
        private final SkyDesign design;

        Preset(String key, String name, String description, int facing, SkyDesign design) {
            this.key = key;
            this.name = name;
            this.description = description;
            this.facing = facing;
            this.design = design;
        }

        /** The direction in degrees east of north in the middle of the card image, where the sky shows what the preset is about. */
        public int facing() { return facing; }

        /** The name in harness commands and widget ids: lower-case letters and digits. */
        public String key() { return key; }

        public String name() { return name; }

        /** One short sentence for a player. */
        public String description() { return description; }

        public SkyDesign design() { return design; }
    }

    /** The presets in the order the editor shows them. */
    public static List<Preset> all() {
        return ALL;
    }

    /** The design of the preset with this key in any case, or null. */
    public static SkyDesign byName(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (Preset p : ALL) if (p.key.equals(key)) return p.design;
        return null;
    }

    /** Puts layers together with ids from 1 in the order they are added. */
    private static final class Builder {
        private final List<SkyLayer> layers = new ArrayList<SkyLayer>();

        Builder add(Kind kind) {
            layers.add(SkyLayer.of(layers.size() + 1, kind));
            return this;
        }

        private Builder change(SkyLayer next) {
            layers.set(layers.size() - 1, next);
            return this;
        }

        private SkyLayer last() { return layers.get(layers.size() - 1); }

        Builder param(Param p, int value) { return change(last().withParam(p, value)); }

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
