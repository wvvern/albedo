package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.config.Transform;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.albedo.core.sky.SkyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class SkyProgramTest {
    @Test
    void theBlockIsAWholeNumberOfVec4Values() {
        assertEquals(0, SkyProgram.FLOATS % 4);
        assertEquals(4 + 12 + SkyDesign.MAX_LAYERS * SkyProgram.LAYER_VEC4S * 4 + SkyImage.RING * 4, SkyProgram.FLOATS);
        assertEquals(SkyProgram.LAYER_VEC4S * 4, SkyProgram.LOCAL + 12, "the rotation rows end the layer");
    }

    @Test
    void theUniformsCoverTheBlockInOrderAndTheShaderDeclaresThem() throws IOException {
        String glsl = skyLayersSource();
        int next = 0;
        for (SkyProgram.Uniform u : SkyProgram.Uniform.values()) {
            assertEquals(next, u.from(), u + " starts where the part before it ends");
            assertEquals(0, u.length() % 4, u + " is a whole number of vec4 values");
            assertTrue(glsl.contains("uniform vec4 " + u.glslName() + ";") || glsl.contains("uniform vec4 " + u.glslName() + "["), u.glslName());
            next += u.length();
        }
        assertEquals(SkyProgram.FLOATS, next);
    }

    @Test
    void theShaderHasTheSameLayoutAndCodes() throws IOException {
        String glsl = skyLayersSource();
        String[] defines = {"MAX_LAYERS " + SkyDesign.MAX_LAYERS, "LAYER_VEC4S " + SkyProgram.LAYER_VEC4S, "RING " + SkyImage.RING,
            "METEOR_CHANNELS " + SkyMeteors.CHANNELS, "METEOR_SLOTS_BACK " + SkyMeteors.SLOTS_BACK,
            "BLEND_ADD " + SkyProgram.BLEND_ADD, "BLEND_SCREEN " + SkyProgram.BLEND_SCREEN, "BLEND_MULTIPLY " + SkyProgram.BLEND_MULTIPLY,
            "CLOUD_OCTAVES " + SkyGenerator.CLOUD_OCTAVES, "CLOUD_FIELD_OCTAVES " + SkyGenerator.CLOUD_FIELD_OCTAVES};
        for (String d : defines) assertTrue(glsl.contains("#define ALBEDO_" + d + "\n"), d);
        // The shader reads the values of a layer from these vec4 slots.
        assertEquals(4, SkyProgram.MASK_DARK);
        assertEquals(8, SkyProgram.SCALE);
        assertEquals(12, SkyProgram.COLOR_1);
        assertEquals(15, SkyProgram.TERM_SLOT);
        assertTrue(glsl.contains("int(albedoLayer(layer, 3).w)"), "the term slot is the last value of slot 3");
        assertEquals(20, SkyProgram.LAB_1);
        assertEquals(24, SkyProgram.LAB_2);
        assertEquals(28, SkyProgram.LOCAL);
        assertTrue(glsl.contains("dot(albedoLayer(layer, 7).xyz, dir)"), "the rotation starts at slot 7");
        // The meteors read their speed, length and shower from the last value of slots 4, 5 and 6, and the aurora reads its speed there too.
        assertEquals(4 * 4 + 3, SkyProgram.SPEED);
        assertFalse(glsl.contains("albedoTime"), "the sky has one time, in the header");
        assertEquals(5 * 4 + 3, SkyProgram.LENGTH);
        assertEquals(SkyProgram.LENGTH, SkyProgram.CLOCK);
        assertTrue(glsl.contains("float t = albedoLayer(layer, 5).w;"), "the aurora reads its clock from the last value of slot 5");
        assertTrue(glsl.contains("const float ALBEDO_AURORA_PERIOD = " + Aurora.PERIOD + ";"));
        assertTrue(glsl.contains("#define ALBEDO_METEOR_PERIOD_SLOTS " + SkyMeteors.PERIOD_SLOTS + "\n"));
        // The header is one vec4 and the turn of the sky three more, before the layers.
        assertEquals(4, SkyProgram.WORLD);
        assertTrue(glsl.contains("vec4 SkyHeader;\n    // The rows of the turn") && glsl.contains("vec4 SkyWorld[3];\n    vec4 SkyLayers["));
        assertEquals(6 * 4 + 3, SkyProgram.SHOWER);
        assertTrue(glsl.contains("const float ALBEDO_METEOR_SLOT = " + SkyMeteors.SLOT + ";"));
        assertTrue(glsl.contains("const float ALBEDO_METEOR_TRAIL_END = " + SkyMeteors.TRAIL_END + ";"));
    }

    @Test
    void aSmallStarsLayerHasShorterTrailsSoThatAPixelReadsNoMoreCells() {
        SkyDesign trails = SkyPresets.TRAILS.withTurning(new SkyTurning(100, 1, SkyTurning.Clock.WORLD, 40, SkyTurning.Direction.NORMAL, 0,
                SkyTurning.MAX_TRAILS, 60));
        int stars = 0;
        while (trails.layers().get(stars).kind() != SkyLayer.Kind.STARS) stars++;
        SkyLayer layer = trails.layers().get(stars);
        SkyLayer small = layer.withTransform(layer.transform().with(Transform.Axis.SCALE, 25));
        float full = arc(trails, stars), quarter = arc(trails.withLayer(stars, small), stars);
        assertEquals(Math.toRadians(SkyTurning.MAX_TRAILS), full, 1e-6);
        assertEquals(Math.toRadians(SkyTurning.MAX_TRAILS) * 0.25, quarter, 1e-6, "the arc in cells is never longer than at size 100");
    }

    private static float arc(SkyDesign design, int layer) {
        return program(design).block()[SkyProgram.index(layer, SkyProgram.TRAIL)];
    }

    @Test
    void theBlockOfAFrameHasTheTimeTheAuroraClockAndTheTurn() {
        SkyProgram aurora = program(SkyPresets.AURORA);
        float[] still = aurora.block();
        assertEquals(SkyProgram.STILL, still[SkyProgram.TIME]);
        assertArrayEquals(new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0},
                Arrays.copyOfRange(still, SkyProgram.WORLD, SkyProgram.LAYERS), "a still sky does not turn");
        SkyTurn turn = SkyTurn.of(SkyPresets.TRAILS.turning(), 0.25);
        float[] frame = aurora.at(3600 + 12.5, turn);
        assertEquals(12.5f, frame[SkyProgram.TIME], "the header time wraps after one period of the clock");
        int layer = 0;
        while ((int) still[SkyProgram.index(layer, SkyProgram.KIND)] != SkyProgram.KIND_AURORA) layer++;
        float speed = still[SkyProgram.index(layer, SkyProgram.SPEED)];
        assertEquals(Aurora.clock(3612.5, speed), frame[SkyProgram.index(layer, SkyProgram.CLOCK)]);
        float[] m = turn.matrix();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) assertEquals(m[col * 4 + row], frame[SkyProgram.WORLD + row * 4 + col], 1e-6f);
        }
        assertEquals(0f, still[SkyProgram.index(layer, SkyProgram.CLOCK)], "the bake draws the aurora at clock 0");
    }

    @Test
    void theMeteorsPresetDrawsSharpAndMovesWithTheTime() {
        SkyProgram meteors = SkyGenerator.bake(SkyPresets.METEORS, 64, 32).program();
        assertTrue(meteors.drawsSharp());
        assertTrue(meteors.moves());
        assertEquals(SkyProgram.STILL, meteors.block()[SkyProgram.TIME], "the block is a still sky until the adapter writes the time");
        assertEquals(SkyProgram.KIND_METEORS, meteors.block()[SkyProgram.index(3, SkyProgram.KIND)]);
        assertEquals(0.7f, meteors.block()[SkyProgram.index(3, SkyProgram.SHOWER)], 1e-6f);
        assertFalse(SkyGenerator.bake(SkyPresets.DUSK, 64, 32).program().moves());
        SkyDesign hidden = SkyPresets.METEORS.withLayer(3, SkyPresets.METEORS.layers().get(3).withOutput(SkyLayer.Output.MASK_ONLY))
                .withLayer(2, SkyPresets.METEORS.layers().get(2).withParam(SkyLayer.Param.TWINKLE, 0));
        assertFalse(program(hidden).moves(), "a meteors layer that does not show or mask does not move");
        SkyLayer masked = SkyLayer.of(5, SkyLayer.Kind.GRADIENT).withMask(SkyMask.of(4, SkyMask.Mode.LIGHT));
        List<SkyLayer> layers = new ArrayList<SkyLayer>(hidden.layers());
        layers.add(masked);
        assertTrue(program(hidden.withLayers(layers)).moves(), "a meteors mask moves");
        SkyProgram aurora = SkyGenerator.bake(SkyPresets.AURORA, 64, 32).program();
        assertTrue(aurora.moves(), "the aurora moves with the same time");
        assertEquals(SkyProgram.STILL, aurora.block()[SkyProgram.TIME]);
    }

    @Test
    void theTwinkleAndThePulseMoveTheSkyOnlyWhenTheyAreOn() throws IOException {
        SkyLayer stars = SkyLayer.of(1, SkyLayer.Kind.STARS), clouds = SkyLayer.of(2, SkyLayer.Kind.FRACTAL);
        assertFalse(program(stars, clouds).moves(), "the default stars and clouds are still");
        SkyProgram twinkling = program(stars.withParam(SkyLayer.Param.TWINKLE, 40), clouds);
        assertTrue(twinkling.moves());
        assertEquals(0.4f, twinkling.block()[SkyProgram.index(0, SkyProgram.TWINKLE)], 1e-6f);
        SkyProgram pulsing = program(stars, clouds.withParam(SkyLayer.Param.PULSE, 60));
        assertTrue(pulsing.moves());
        assertEquals(0.6f, pulsing.block()[SkyProgram.index(1, SkyProgram.PULSE)], 1e-6f);
        // The stars and the clouds read the twinkle and the pulse from the last value of slot 4, as the bake does.
        assertEquals(4 * 4 + 3, SkyProgram.TWINKLE);
        assertEquals(4 * 4 + 3, SkyProgram.PULSE);
        String glsl = skyLayersSource();
        assertTrue(glsl.contains("float twinkle = (flags & ALBEDO_FLAG_TWINKLE) != 0 ? albedoLayer(layer, 4).w : 0.0"));
        assertTrue(glsl.contains("float amount = albedoLayer(layer, 4).w"));
    }

    @Test
    void theTrailsAreInTheBlockOnlyForTheStarsOfASkyThatTurns() throws IOException {
        SkyLayer stars = SkyLayer.of(1, SkyLayer.Kind.STARS).withTransform(new Transform(70, 20, 10, 100));
        SkyLayer galaxy = SkyLayer.of(2, SkyLayer.Kind.GALAXY);
        SkyDesign still = design(stars, galaxy);
        SkyTurning turning = new SkyTurning(100, 2, SkyTurning.Clock.REAL, 40, SkyTurning.Direction.REVERSE, 30, 45, 70);
        float[] block = program(still.withTurning(turning)).block();
        assertEquals((float) Math.toRadians(45), block[SkyProgram.index(0, SkyProgram.TRAIL)], 1e-6f);
        assertEquals(0.7f, block[SkyProgram.index(0, SkyProgram.TRAIL_LIGHT)], 1e-6f);
        double[] axis = SkyTurn.trailAxis(turning);
        for (int row = 0; row < 3; row++) {
            double v = 0;
            for (int c = 0; c < 3; c++) v += block[SkyProgram.index(0, SkyProgram.LOCAL + row * 4 + c)] * axis[c];
            assertEquals(v, block[SkyProgram.index(0, SkyProgram.TRAIL_AXIS + row * 4)], 1e-6, "the axis in the layer frame, row " + row);
        }
        assertArrayEquals(program(still).block(), program(still.withTurning(turning.withTrails(0))).block(), "no trails");
        assertArrayEquals(program(still).block(), program(still.withTurning(turning.withAmount(0))).block(), "a still sky has no trails");
        // The stars read the trail arc, the trail light and the axis from the last values of slots 5 to 9, as the bake does.
        assertEquals(5 * 4 + 3, SkyProgram.TRAIL);
        assertEquals(6 * 4 + 3, SkyProgram.TRAIL_LIGHT);
        assertEquals(7 * 4 + 3, SkyProgram.TRAIL_AXIS);
        String glsl = skyLayersSource();
        assertTrue(glsl.contains("? vec4(albedoLayer(layer, 7).w, albedoLayer(layer, 8).w, albedoLayer(layer, 9).w, albedoLayer(layer, 5).w)"));
        assertTrue(glsl.contains("seed, trail, albedoLayer(layer, 6).w);"));
    }

    @Test
    void allPresetsDrawSharp() {
        SkyProgram dusk = SkyGenerator.bake(SkyPresets.DUSK, 64, 32).program();
        assertTrue(dusk.drawsSharp());
        assertEquals(0, dusk.terms());
        SkyProgram nebula = SkyGenerator.bake(SkyPresets.NEBULA, 64, 32).program();
        assertTrue(nebula.drawsSharp(), "the blur and the bloom are terms, the hue shift is calculated for each pixel");
        assertEquals(4, nebula.terms(), "two terms and two field images");
        assertEquals(0, nebula.termSlot(3), "the blur");
        assertEquals(-1, nebula.termSlot(4), "the hue shift");
        assertEquals(-1, nebula.termSlot(5), "the stars over the blur");
        assertEquals(1, nebula.termSlot(6), "the bloom");
        assertEquals(2, nebula.termSlot(1), "the field image of the fractal comes after the terms");
        assertEquals(3, nebula.termSlot(2), "the field image of the noise mask");
        assertEquals(SkyProgram.KIND_HUE_SHIFT, nebula.block()[SkyProgram.index(4, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_STARS, nebula.block()[SkyProgram.index(5, SkyProgram.KIND)]);
        SkyProgram galaxy = SkyGenerator.bake(SkyPresets.GALAXY, 64, 32).program();
        assertTrue(galaxy.drawsSharp(), "gradient, noise, stars, galaxy, deep field, fractal and the bloom term");
        assertEquals(SkyProgram.KIND_NOISE, galaxy.block()[SkyProgram.index(1, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_GALAXY, galaxy.block()[SkyProgram.index(3, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_DEEP_FIELD, galaxy.block()[SkyProgram.index(4, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_BLOOM, galaxy.block()[SkyProgram.index(6, SkyProgram.KIND)]);
        assertEquals(3, galaxy.terms());
        assertEquals(3, galaxy.block()[SkyProgram.TERM_COUNT], "the header has the count of term and field images");
        assertEquals(-1, galaxy.termSlot(3));
        assertEquals(-1, galaxy.termSlot(4), "the deep field is calculated for each pixel");
        assertEquals(0, galaxy.termSlot(6));
        assertEquals(1, galaxy.termSlot(1), "the field image of the noise");
        assertEquals(2, galaxy.termSlot(5), "the field image of the fractal");
        assertTrue(galaxy.moves(), "the stars twinkle and the galaxy moves");
        SkyLayer[] clouds = new SkyLayer[SkyDesign.MAX_LAYERS];
        for (int i = 0; i < clouds.length; i++) clouds[i] = SkyLayer.of(i + 1, SkyLayer.Kind.NOISE);
        assertEquals(SkyProgram.MAX_SLOTS, program(clouds).terms(), "the field images stop at the largest number of images");
        SkyProgram aurora = SkyGenerator.bake(SkyPresets.AURORA, 64, 32).program();
        assertTrue(aurora.drawsSharp(), "gradient, glow, stars and aurora");
        assertEquals(0, aurora.terms());
        assertEquals(SkyProgram.KIND_AURORA, aurora.block()[SkyProgram.index(3, SkyProgram.KIND)]);
        assertEquals(0.5f, aurora.block()[SkyProgram.index(3, SkyProgram.SPEED)]);
    }

    @Test
    void theGalaxyValuesAreInTheFreeFloatsOfTheLayer() {
        SkyLayer g = SkyLayer.of(1, SkyLayer.Kind.GALAXY).withParam(SkyLayer.Param.TILT, 45).withParam(SkyLayer.Param.DUST, 30)
                .withParam(SkyLayer.Param.JETS, 80).withParam(SkyLayer.Param.COLOR_3, 0xFFFF0000);
        float[] b = program(SkyPresets.GALAXY.withLayers(Collections.singletonList(g))).block();
        assertEquals(0.5f, b[SkyProgram.index(0, SkyProgram.TILT)], 1e-6f);
        assertEquals(0.3f, b[SkyProgram.index(0, SkyProgram.DUST)], 1e-6f);
        assertEquals(0.8f, b[SkyProgram.index(0, SkyProgram.JETS)], 1e-6f);
        assertEquals(1f, b[SkyProgram.index(0, SkyProgram.COLOR_3)], 1e-6f);
        assertEquals(0f, b[SkyProgram.index(0, SkyProgram.COLOR_3 + 4)], 1e-6f);
        // The rotation rows keep their own floats.
        assertEquals(1f, b[SkyProgram.index(0, SkyProgram.LOCAL)] * b[SkyProgram.index(0, SkyProgram.LOCAL)]
                + b[SkyProgram.index(0, SkyProgram.LOCAL + 1)] * b[SkyProgram.index(0, SkyProgram.LOCAL + 1)]
                + b[SkyProgram.index(0, SkyProgram.LOCAL + 2)] * b[SkyProgram.index(0, SkyProgram.LOCAL + 2)], 1e-5f);
        int[] used = {SkyProgram.KIND, SkyProgram.BLEND, SkyProgram.USE, SkyProgram.MASK_INDEX, SkyProgram.MASK_DARK, SkyProgram.OPACITY,
            SkyProgram.AMOUNT, SkyProgram.HEIGHT, SkyProgram.SCALE, SkyProgram.SEED, SkyProgram.ALPHA_1, SkyProgram.ALPHA_2,
            SkyProgram.TERM_SLOT, SkyProgram.TILT, SkyProgram.DUST, SkyProgram.JETS};
        Set<Integer> taken = new HashSet<Integer>();
        for (int at : used) assertTrue(taken.add(at), "offset " + at);
        for (int c = 0; c < 3; c++) {
            for (int base : new int[]{SkyProgram.COLOR_1, SkyProgram.COLOR_2, SkyProgram.LAB_1, SkyProgram.LAB_2}) {
                assertTrue(taken.add(base + c), "offset " + (base + c));
            }
            assertTrue(taken.add(SkyProgram.COLOR_3 + c * 4), "jet color " + c);
            for (int r = 0; r < 3; r++) assertTrue(taken.add(SkyProgram.LOCAL + r * 4 + c), "rotation " + r);
        }
        assertEquals(SkyProgram.LAYER_VEC4S * 4, taken.size(), "each float of the layer has one use");
    }

    @Test
    void theDetailAndTheMotionOfAGalaxyAreInFloatsThatAGalaxyDoesNotRead() throws IOException {
        SkyLayer plain = SkyLayer.of(1, SkyLayer.Kind.GALAXY);
        SkyLayer fine = plain.withParam(SkyLayer.Param.DETAIL, 70).withParam(SkyLayer.Param.MOTION, 40);
        SkyProgram p = program(fine);
        assertEquals(0.7f, p.block()[SkyProgram.index(0, SkyProgram.DETAIL)], 1e-6f);
        assertEquals(0.4f, p.block()[SkyProgram.index(0, SkyProgram.MOTION)], 1e-6f);
        // A galaxy has no height and mixes no OKLab colors, so the detail is in slot 1 and the motion in slot 5, as the shader reads them.
        assertEquals(SkyProgram.HEIGHT, SkyProgram.DETAIL);
        assertEquals(SkyProgram.LAB_1, SkyProgram.MOTION);
        assertEquals(1 * 4 + 3, SkyProgram.DETAIL);
        assertEquals(5 * 4, SkyProgram.MOTION);
        String glsl = skyLayersSource();
        assertTrue(glsl.contains("g.detail = (flags & ALBEDO_FLAG_DETAIL) != 0 ? values.w : 0.0;"));
        assertTrue(glsl.contains("g.motion = (flags & ALBEDO_FLAG_MOTION) != 0 ? albedoLayer(layer, 5).x : 0.0;"));
        assertTrue(p.moves(), "a galaxy with motion needs the time of each frame");
        assertFalse(program(plain).moves());
        assertFalse(program(plain.withParam(SkyLayer.Param.DETAIL, 100)).moves(), "the detail alone is still");
        int both = SkyLayerSteps.FLAG_DUST | SkyLayerSteps.FLAG_JETS | SkyLayerSteps.FLAG_DETAIL | SkyLayerSteps.FLAG_MOTION;
        assertTrue(p.shaderSteps().contains(" s = albedoGalaxy(0, albedoFrame(0, dir), footprint, " + both + ");"), p.shaderSteps());
        int old = SkyLayerSteps.FLAG_DUST | SkyLayerSteps.FLAG_JETS;
        assertTrue(program(plain).shaderSteps().contains("footprint, " + old + ");"), "a plain galaxy compiles none of the new parts");
    }

    @Test
    void theFiltersThatReadOtherPixelsAreTermsAndTheFiltersHaveTheirOwnCodes() {
        Set<Integer> codes = new HashSet<Integer>();
        for (SkyLayer.Kind kind : SkyLayer.Kind.values()) {
            boolean readsOthers = kind == SkyLayer.Kind.BLUR || kind == SkyLayer.Kind.BLOOM || kind == SkyLayer.Kind.PIXELATE
                    || kind == SkyLayer.Kind.CRYSTALLIZE || kind == SkyLayer.Kind.SWIRL;
            assertEquals(readsOthers ? SkyProgram.Path.TERM : SkyProgram.Path.PIXEL, ShaderKind.of(kind).path, kind.name());
            boolean filter = kind.role() == SkyLayer.Role.FILTER;
            int code = ShaderKind.of(kind).code;
            assertEquals(filter, code >= SkyProgram.KIND_BLOOM && code <= SkyProgram.KIND_VIGNETTE, kind.name());
            assertTrue(code > 0, kind.name());
            assertTrue(codes.add(code), "each kind has its own code: " + kind.name());
        }
    }

    @Test
    void aWarpTermHoldsTheFullResultOfTheFilter() {
        int w = 128, h = 64;
        List<SkyLayer> layers = new ArrayList<SkyLayer>(SkyPresets.GALAXY.layers().subList(0, 4));
        layers.add(SkyLayer.of(5, SkyLayer.Kind.SWIRL));
        SkyImage img = SkyGenerator.bake(SkyPresets.GALAXY.withLayers(layers), w, h);
        assertEquals(0, img.program().termSlot(4));
        double error = 0;
        int count = 0;
        for (int ty = 0; ty < h / 2; ty++) {
            for (int tx = 0; tx < w / 2; tx++) {
                float sum = 0;
                for (int y = 2 * ty; y <= 2 * ty + 1; y++) {
                    for (int x = 2 * tx; x <= 2 * tx + 1; x++) sum += Oklab.toLinear(img.argb()[y * w + x] >> 8);
                }
                error += Math.abs(sum / 4 - HalfFloat.decode(img.terms()[(ty * (w / 2) + tx) * 4 + 1]));
                count++;
            }
        }
        assertTrue(error / count < 0.004, "the term is the swirled sky at half size: " + error / count);
    }

    @Test
    void aBloomAfterTheLastTermSlotIsBaked() {
        List<SkyLayer> layers = new ArrayList<SkyLayer>();
        layers.add(SkyLayer.of(1, SkyLayer.Kind.GRADIENT));
        for (int i = 0; i <= SkyProgram.MAX_TERMS; i++) layers.add(SkyLayer.of(i + 2, SkyLayer.Kind.BLOOM));
        SkyProgram p = program(SkyPresets.GALAXY.withLayers(layers));
        assertEquals(SkyProgram.MAX_TERMS, p.terms());
        assertEquals(SkyProgram.MAX_TERMS - 1, p.termSlot(SkyProgram.MAX_TERMS));
        assertEquals(SkyProgram.Path.BAKED, p.path(SkyProgram.MAX_TERMS + 1));
        assertEquals(-1, p.termSlot(SkyProgram.MAX_TERMS + 1));
        assertFalse(p.drawsSharp());
    }

    @Test
    void aSkyThatCannotDrawSharpHasNoFieldImages() {
        List<SkyLayer> layers = new ArrayList<SkyLayer>();
        layers.add(SkyLayer.of(1, SkyLayer.Kind.NOISE));
        for (int i = 0; i <= SkyProgram.MAX_TERMS; i++) layers.add(SkyLayer.of(i + 2, SkyLayer.Kind.BLOOM));
        SkyImage img = SkyGenerator.bake(SkyPresets.GALAXY.withLayers(layers), 64, 32);
        assertFalse(img.program().drawsSharp());
        assertEquals(-1, img.program().termSlot(0), "the clouds");
        assertEquals(SkyProgram.MAX_TERMS, img.program().terms(), "only the term images of the blooms");
        assertEquals(32 * 16 * 4 * SkyProgram.MAX_TERMS, img.terms().length);
    }

    @Test
    void theDuskBlockHasTheLayersTheMaskAndTheHorizonColors() {
        SkyImage img = SkyGenerator.bake(SkyPresets.DUSK, 64, 32);
        float[] b = img.program().block();
        assertEquals(4, b[SkyProgram.LAYER_COUNT], "four layers");
        assertEquals(1, b[SkyProgram.SPHERE], "the sky goes around the full sphere");
        assertEquals(SkyProgram.KIND_GRADIENT, b[SkyProgram.index(0, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_GLOW, b[SkyProgram.index(1, SkyProgram.KIND)]);
        assertEquals(SkyProgram.USE_MASK, b[SkyProgram.index(2, SkyProgram.USE)], "the mask only layer is made for the stars");
        assertEquals(SkyProgram.KIND_STARS, b[SkyProgram.index(3, SkyProgram.KIND)]);
        assertEquals(2, b[SkyProgram.index(3, SkyProgram.MASK_INDEX)], "the stars use the third layer as their mask");
        assertEquals(1, b[SkyProgram.index(3, SkyProgram.MASK_DARK)]);
        assertEquals(SkyProgram.BLEND_ADD, b[SkyProgram.index(3, SkyProgram.BLEND)]);
        assertEquals(0.75f, b[SkyProgram.index(3, SkyProgram.OPACITY)], 1e-6f);
        assertEquals(-1, b[SkyProgram.index(0, SkyProgram.MASK_INDEX)]);
        assertEquals(1f, b[SkyProgram.index(0, SkyProgram.LOCAL)], 1e-6f, "an identity rotation");
    }
}
