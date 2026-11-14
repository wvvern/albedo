package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyTurning;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

/** Designs, programs, pixel channels and shader text for the tests of the sky package. */
final class SkyFixtures {
    /** A full turning with the pole on the north horizon, which turns as the vanilla sun does. */
    static final SkyTurning SUN = new SkyTurning(100, 1, SkyTurning.Clock.WORLD, 0, SkyTurning.Direction.NORMAL, 0);

    private SkyFixtures() {
    }

    /** A blank ring of horizon colors. */
    static float[] ring() {
        return new float[SkyImage.RING * 3];
    }

    /** A full sphere with the layers, no sun or moon and a fixed light. */
    static SkyDesign design(SkyLayer... layers) {
        return new SkyDesign(Arrays.asList(layers), SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED, SkyDesign.Extent.SPHERE);
    }

    /** One layer with no sun or moon and a fixed light. */
    static SkyDesign single(SkyLayer layer, SkyDesign.Extent extent) {
        return new SkyDesign(Collections.singletonList(layer), SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED, extent);
    }

    static SkyProgram program(SkyDesign design) {
        return SkyProgram.of(design, ring());
    }

    static SkyProgram program(SkyLayer... layers) {
        return program(design(layers));
    }

    static int red(int argb) {
        return argb >> 16 & 0xFF;
    }

    static int blue(int argb) {
        return argb & 0xFF;
    }

    /** The unit direction at an azimuth east of north (-z) and an elevation, in radians. */
    static float[] direction(double azimuth, double elevation) {
        return new float[] {(float) (Math.cos(elevation) * Math.sin(azimuth)), (float) Math.sin(elevation),
            (float) (-Math.cos(elevation) * Math.cos(azimuth))};
    }

    /** The row whose center is nearest to an elevation in degrees. */
    static int row(double elevation, int h) {
        return (int) Math.round((SkyImage.TOP - elevation) / (SkyImage.TOP - SkyImage.BOTTOM) * h - 0.5);
    }

    /** The text of the sky include of the shaders, which the tests compare with the numbers of the bake. */
    static String skyLayersSource() throws IOException {
        return SkyShaderSource.read("include/sky_layers.glsl");
    }

    /** A float array as a GLSL vec3 constant. */
    static String vec3(float[] v) {
        return "vec3(" + v[0] + ", " + v[1] + ", " + v[2] + ")";
    }
}
