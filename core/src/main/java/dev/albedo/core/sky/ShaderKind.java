package dev.albedo.core.sky;

import dev.albedo.core.config.SkyLayer;

import java.util.EnumMap;
import java.util.Map;

/** The kind code and the {@link SkyProgram.Path} of each layer kind in the sky shader. */
enum ShaderKind {
    GRADIENT(SkyLayer.Kind.GRADIENT, SkyProgram.KIND_GRADIENT, SkyProgram.Path.PIXEL),
    GLOW(SkyLayer.Kind.GLOW, SkyProgram.KIND_GLOW, SkyProgram.Path.PIXEL),
    STARS(SkyLayer.Kind.STARS, SkyProgram.KIND_STARS, SkyProgram.Path.PIXEL),
    NOISE(SkyLayer.Kind.NOISE, SkyProgram.KIND_NOISE, SkyProgram.Path.PIXEL),
    FRACTAL(SkyLayer.Kind.FRACTAL, SkyProgram.KIND_FRACTAL, SkyProgram.Path.PIXEL),
    GALAXY(SkyLayer.Kind.GALAXY, SkyProgram.KIND_GALAXY, SkyProgram.Path.PIXEL),
    METEORS(SkyLayer.Kind.METEORS, SkyProgram.KIND_METEORS, SkyProgram.Path.PIXEL),
    AURORA(SkyLayer.Kind.AURORA, SkyProgram.KIND_AURORA, SkyProgram.Path.PIXEL),
    DEEP_FIELD(SkyLayer.Kind.DEEP_FIELD, SkyProgram.KIND_DEEP_FIELD, SkyProgram.Path.PIXEL),
    BLOOM(SkyLayer.Kind.BLOOM, SkyProgram.KIND_BLOOM, SkyProgram.Path.TERM),
    BLUR(SkyLayer.Kind.BLUR, SkyProgram.KIND_BLUR, SkyProgram.Path.TERM),
    PIXELATE(SkyLayer.Kind.PIXELATE, SkyProgram.KIND_PIXELATE, SkyProgram.Path.TERM),
    CRYSTALLIZE(SkyLayer.Kind.CRYSTALLIZE, SkyProgram.KIND_CRYSTALLIZE, SkyProgram.Path.TERM),
    SWIRL(SkyLayer.Kind.SWIRL, SkyProgram.KIND_SWIRL, SkyProgram.Path.TERM),
    POSTERIZE(SkyLayer.Kind.POSTERIZE, SkyProgram.KIND_POSTERIZE, SkyProgram.Path.PIXEL),
    HUE_SHIFT(SkyLayer.Kind.HUE_SHIFT, SkyProgram.KIND_HUE_SHIFT, SkyProgram.Path.PIXEL),
    VIGNETTE(SkyLayer.Kind.VIGNETTE, SkyProgram.KIND_VIGNETTE, SkyProgram.Path.PIXEL);

    private static final Map<SkyLayer.Kind, ShaderKind> BY_KIND = new EnumMap<SkyLayer.Kind, ShaderKind>(SkyLayer.Kind.class);

    static {
        for (ShaderKind k : values()) BY_KIND.put(k.kind, k);
    }

    private final SkyLayer.Kind kind;
    /** The kind code of the shader, where the filters are the codes from {@link SkyProgram#KIND_BLOOM} to {@link SkyProgram#KIND_VIGNETTE}. */
    final int code;
    /** A filter that reads near pixels or other directions is a term image; the other kinds are pixels. */
    final SkyProgram.Path path;

    ShaderKind(SkyLayer.Kind kind, int code, SkyProgram.Path path) {
        this.kind = kind;
        this.code = code;
        this.path = path;
    }

    /** The shader kind of a layer kind. */
    static ShaderKind of(SkyLayer.Kind kind) {
        ShaderKind k = BY_KIND.get(kind);
        if (k == null) throw new IllegalArgumentException("no shader kind for " + kind);
        return k;
    }
}
