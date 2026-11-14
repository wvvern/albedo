package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.Transform;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static dev.albedo.core.sky.SkyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class DeepFieldTest {
    private static final int W = 1024, H = 512;

    private static int[] bake(SkyLayer field) {
        return SkyGenerator.bake(design(field), W, H).argb();
    }

    private static SkyLayer field() {
        return SkyLayer.of(1, SkyLayer.Kind.DEEP_FIELD).withTransform(Transform.IDENTITY);
    }

    /** The count of pixels with light in the rows from {@code from} to {@code to}. */
    private static int lit(int[] px, int from, int to) {
        int n = 0;
        for (int i = from * W; i < to * W; i++) if (red(px[i]) > 12) n++;
        return n;
    }

    @Test
    void theFarGalaxiesAreAllOverTheSkyAndTheCountSetsHowMany() {
        SkyLayer galaxies = field().withParam(SkyLayer.Param.HEIGHT, 0);
        int[] few = bake(galaxies.withParam(SkyLayer.Param.AMOUNT, 10)), many = bake(galaxies.withParam(SkyLayer.Param.AMOUNT, 90));
        assertTrue(lit(many, 0, H) > 2 * lit(few, 0, H), "lit pixels: " + lit(few, 0, H) + " and " + lit(many, 0, H));
        // Each band of 30 degrees of elevation has some, also below the horizon.
        for (int band = 0; band < 6; band++) assertTrue(lit(many, band * H / 6, (band + 1) * H / 6) > 20, "band " + band);
        for (int y = 0; y < H; y++) {
            assertTrue(Math.abs(red(many[y * W]) - red(many[y * W + W - 1])) < 40, "no seam where the image wraps, row " + y);
        }
    }

    @Test
    void theFarGalaxiesHaveTheTwoColorsOfTheLayer() {
        SkyLayer galaxies = field().withParam(SkyLayer.Param.HEIGHT, 0).withParam(SkyLayer.Param.AMOUNT, 90)
                .withParam(SkyLayer.Param.COLOR_1, 0xFFFF0000).withParam(SkyLayer.Param.COLOR_2, 0xFF0000FF);
        int redder = 0, bluer = 0;
        for (int argb : bake(galaxies)) {
            int r = red(argb), b = blue(argb);
            assertEquals(0, argb >> 8 & 0xFF, 1, "no green in a galaxy of red and blue");
            if (r > b + 40) redder++;
            if (b > r + 40) bluer++;
        }
        assertTrue(redder > 50 && bluer > 50, "old galaxies in the first color and young ones in the second: " + redder + " and " + bluer);
    }

    @Test
    void theStarCloudIsABandAlongTheHorizonOfTheLayerThatIsBrightestAtItsCenter() {
        SkyLayer none = field().withParam(SkyLayer.Param.AMOUNT, 0).withParam(SkyLayer.Param.HEIGHT, 0);
        int[] without = bake(none), with = bake(none.withParam(SkyLayer.Param.HEIGHT, 100));
        // The layer center is the north, at the first and the last column, and the south is the middle column.
        long north = 0, south = 0;
        for (int y = H / 2 - H / 12; y < H / 2 + H / 12; y++) {
            for (int x = 0; x < W / 8; x++) {
                north += red(with[y * W + x]) - red(without[y * W + x]) + red(with[y * W + W - 1 - x]) - red(without[y * W + W - 1 - x]);
                south += red(with[y * W + W / 2 - W / 8 + x]) - red(without[y * W + W / 2 - W / 8 + x])
                        + red(with[y * W + W / 2 + x]) - red(without[y * W + W / 2 + x]);
            }
        }
        assertTrue(north > 20000, "the cloud adds light at the center of the layer: " + north);
        assertTrue(north > 1.5 * south, "and less on the far side: " + north + " and " + south);
        // 50 degrees from the band the sky is as with no cloud.
        for (int y = 0; y < H * 40 / 180; y++) for (int x = 0; x < W; x++) assertEquals(without[y * W + x], with[y * W + x], "row " + y);
    }

    @Test
    void theShaderHasTheSameNumbers() throws IOException {
        String glsl = skyLayersSource();
        String[] numbers = {
            "float ALBEDO_FIELD_CELLS = " + DeepField.CELLS,
            "float ALBEDO_FIELD_CLOUD_CELLS = " + DeepField.CLOUD_CELLS,
            "float ALBEDO_FIELD_CLOUD_WIDTH = " + DeepField.CLOUD_WIDTH,
            "float ALBEDO_FIELD_CLOUD_LENGTH = " + DeepField.CLOUD_LENGTH,
            "vec3 ALBEDO_FIELD_CLOUD_GLOW = " + vec3(DeepField.CLOUD_GLOW)};
        for (String n : numbers) assertTrue(glsl.contains("const " + n + ";\n"), n);
    }

    @Test
    void theStepOfTheShaderHasTheCloudOnlyWhenItShows() {
        SkyDesign with = design(field());
        SkyDesign without = with.withLayers(Collections.singletonList(field().withParam(SkyLayer.Param.HEIGHT, 0)));
        String cloud = SkyLayerSteps.layerSteps(program(with));
        assertTrue(cloud.contains(" s = albedoDeepField(0, albedoFrame(0, dir), footprint, " + SkyLayerSteps.FLAG_CLOUD + ");"), cloud);
        String plain = SkyLayerSteps.layerSteps(program(without));
        assertTrue(plain.contains(" s = albedoDeepField(0, albedoFrame(0, dir), footprint, 0);"), plain);
        assertFalse(program(with).moves(), "the deep field is still");
    }
}
