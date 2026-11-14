package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.shader.ShaderSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static dev.albedo.core.sky.SkyFixtures.design;
import static org.junit.jupiter.api.Assertions.*;

class SkyLayerStepsTest {
    @Test
    void eachClassicShaderIsOneTextWithItsVersionFirst() throws IOException {
        String steps = SkyLayerSteps.layerSteps(SkyGenerator.bake(SkyPresets.NEBULA, 64, 32).program());
        for (ShaderSource.Dialect dialect : ShaderSource.Dialect.values()) {
            for (String name : new String[] {SkyLayerSteps.SHARP, SkyLayerSteps.EQUIRECT}) {
                for (boolean vertex : new boolean[] {true, false}) {
                    String text = ShaderSource.classic(name, vertex, dialect, SkyLayerSteps.includes(steps));
                    String what = dialect + " " + name + (vertex ? ".vsh" : ".fsh");
                    assertTrue(text.startsWith("#version "), what);
                    assertEquals(1, count(text, "#version"), what);
                    assertFalse(text.contains("#include"), what + " has no include step");
                    assertEquals(!vertex, text.contains("vec3 albedoSky(vec3 dir, float footprint)"), what);
                    assertEquals(!vertex, text.contains(steps), what + " has the layer steps");
                }
            }
        }
    }

    @Test
    void onlyAClassicFragmentShaderDeclaresTheFragmentOutput() throws IOException {
        for (ShaderSource.Dialect dialect : ShaderSource.Dialect.values()) {
            for (String name : new String[] {SkyLayerSteps.SHARP, SkyLayerSteps.EQUIRECT, ShaderSource.CHROMA}) {
                String text = ShaderSource.classic(name, true, dialect);
                assertFalse(text.contains("fragColor") || text.contains("ALBEDO_FRAG_COLOR"), dialect + " " + name + ".vsh");
            }
            for (String name : new String[] {SkyLayerSteps.SHARP, SkyLayerSteps.EQUIRECT, ShaderSource.CHROMA, SkyLayerSteps.BLACK}) {
                String text = ShaderSource.classic(name, false, dialect);
                assertEquals(1, count(text, "out vec4 fragColor;"), dialect + " " + name + ".fsh");
                assertFalse(text.contains("#include"), dialect + " " + name + ".fsh");
            }
        }
    }

    @Test
    void theBakedSkyShaderIsOneTextWithNoSkyInclude() throws IOException {
        for (boolean vertex : new boolean[] {true, false}) {
            String text = ShaderSource.classic(SkyLayerSteps.BAKED, vertex, ShaderSource.Dialect.UNIFORMS_150);
            assertTrue(text.startsWith("#version 150\n"), text);
            assertFalse(text.contains("#include"), text);
            assertFalse(text.contains("albedoSky"), "the baked image needs no sky layers");
            assertEquals(vertex, text.contains("ProjMat * ModelViewMat"), text);
            assertEquals(!vertex, text.contains("texture(Sampler0, skyUv) * ColorModulator"), text);
        }
    }

    @Test
    void theLayerStepsHaveTheStructureOfEachLayerThatShowsOrMasksAsConstants() {
        // Nebula: gradient, fractal with pulse, noise as a mask, blur and hue shift through the mask, stars that twinkle, bloom.
        String steps = SkyLayerSteps.layerSteps(SkyGenerator.bake(SkyPresets.NEBULA, 64, 32).program());
        assertTrue(steps.startsWith("#define ALBEDO_LAYER_STEPS ") && steps.endsWith("\n") && count(steps, "\n") == 1, steps);
        assertEquals(7, count(steps, " s = "), steps);
        assertEquals(6, count(steps, "img = albedoShow("), "the noise only masks: " + steps);
        String gradient = " s = albedoGradient(0, albedoFrame(0, dir)); img = albedoShow(img, s, 0, 0, -1, false);";
        assertTrue(steps.contains(gradient), steps);
        String fractal = " s = albedoFractal(1, albedoFrame(1, dir), albedoField(2, dir), 8); img = albedoShow(img, s, 1, 1, -1, false);";
        assertTrue(steps.contains(fractal), "the fractal reads its field image, pulses and adds: " + steps);
        assertTrue(steps.contains(" s = albedoClouds(2, albedoFrame(2, dir), albedoField(3, dir), 0); albedoMaskSource(2, s);"), steps);
        SkyLayer[] clouds = new SkyLayer[SkyDesign.MAX_LAYERS];
        for (int i = 0; i < clouds.length; i++) clouds[i] = SkyLayer.of(i + 1, SkyLayer.Kind.NOISE);
        String last = SkyLayerSteps.layerSteps(SkyGenerator.bake(design(clouds), 64, 32).program());
        assertTrue(last.contains(" s = albedoClouds(7, albedoFrame(7, dir), albedoCloudsField(7, albedoFrame(7, dir), 0), 0);"),
                "a layer with no field image: " + last);
        assertTrue(steps.contains(" s = albedoTermFilter(3, dir); img = albedoShow(img, s, 3, 0, 2, false);"), "the blur in the light: " + steps);
        assertTrue(steps.contains(" s = vec4(albedoHueShift(4, img), 1.0); img = albedoShow(img, s, 4, 0, 2, true);"), "the hue shift in the dark");
        assertTrue(steps.contains(" s = albedoStars(5, albedoFrame(5, dir), footprint, 5);"), "the stars twinkle with spikes: " + steps);
        assertTrue(steps.contains(" s = albedoBloom(6, img, dir);"), steps);
        // Dusk: its third layer masks the stars, and a mask that no layer uses has no step.
        SkyProgram dusk = SkyGenerator.bake(SkyPresets.DUSK, 64, 32).program();
        assertEquals(4, count(SkyLayerSteps.layerSteps(dusk), " s = "));
        assertFalse(SkyLayerSteps.readsTerms(dusk.shaderSteps()), "dusk has no term or field image");
        assertTrue(SkyLayerSteps.readsTerms(steps), "nebula has both");
        SkyProgram bloom = SkyGenerator.bake(SkyPresets.DUSK.withAdded(SkyLayer.Kind.BLOOM), 64, 32).program();
        assertTrue(SkyLayerSteps.readsTerms(bloom.shaderSteps()), "a bloom term");
        SkyProgram lower = SkyGenerator.bake(SkyPresets.DUSK.withLayers(SkyPresets.DUSK.layers().subList(0, 3)), 64, 32).program();
        assertEquals(2, count(SkyLayerSteps.layerSteps(lower), " s = "));
        assertSame(dusk.shaderSteps(), dusk.shaderSteps(), "the program keeps its steps");
    }

    @Test
    void theShaderHasTheSameFlags() throws IOException {
        String glsl = SkyFixtures.skyLayersSource();
        String[] names = {"TWINKLE", "TRAILS", "SPIKES", "PULSE", "DUST", "JETS", "DETAIL", "MOTION", "CLOUD"};
        int[] bits = {SkyLayerSteps.FLAG_TWINKLE, SkyLayerSteps.FLAG_TRAILS, SkyLayerSteps.FLAG_SPIKES, SkyLayerSteps.FLAG_PULSE,
            SkyLayerSteps.FLAG_DUST, SkyLayerSteps.FLAG_JETS, SkyLayerSteps.FLAG_DETAIL, SkyLayerSteps.FLAG_MOTION,
            SkyLayerSteps.FLAG_CLOUD};
        for (int i = 0; i < names.length; i++) {
            assertEquals(1 << i, bits[i], names[i] + " has its own bit");
            assertTrue(glsl.contains("#define ALBEDO_FLAG_" + names[i] + " " + bits[i] + "\n"), names[i]);
        }
    }

    @Test
    void onlyTheSkyIncludeGetsTheStepsAndAnOtherNamespaceIsAnError() throws IOException {
        ShaderSource.Includes includes = SkyLayerSteps.includes("STEPS\n");
        assertTrue(includes.read(SkyLayerSteps.LAYERS_INCLUDE).startsWith("STEPS\n"));
        assertEquals(ShaderSource.readInclude("albedo:chroma.glsl"), includes.read("albedo:chroma.glsl"));
        assertThrows(IOException.class, () -> includes.read("minecraft:projection.glsl"));
    }

    @Test
    void eachSharpShaderOf26HasNoIncludeLineAfterInline() throws IOException {
        Map<String, String> game = new HashMap<String, String>();
        game.put("minecraft:dynamictransforms.glsl", "uniform DynamicTransforms;\n");
        game.put("minecraft:projection.glsl", "uniform Projection;\n");
        ShaderSource.Includes mod = SkyLayerSteps.includes("#define ALBEDO_LAYER_STEPS\n");
        for (String path : new String[] {"core/sky_sharp.vsh", "core/sky_sharp.fsh", "core/sky_equirect.vsh", "core/sky_equirect.fsh"}) {
            String text = ShaderSource.inline(ShaderSource.read(path), id -> game.containsKey(id) ? game.get(id) : mod.read(id));
            assertFalse(text.contains("#include"), path);
            assertEquals(path.endsWith(".fsh"), text.contains("vec3 albedoSky(vec3 dir, float footprint)"), path);
        }
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) n++;
        return n;
    }
}
