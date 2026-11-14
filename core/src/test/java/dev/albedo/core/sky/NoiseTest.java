package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static dev.albedo.core.sky.SkyFixtures.glslFloat;
import static dev.albedo.core.sky.SkyFixtures.glslInt;
import static dev.albedo.core.sky.SkyFixtures.skyLayersSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NoiseTest {
    @Test
    void theShaderHasTheSameNumbers() throws IOException {
        String glsl = skyLayersSource();
        assertEquals(Noise.LACUNARITY, glslFloat(glsl, "ALBEDO_NOISE_LACUNARITY"));
        assertEquals(Noise.OCTAVE_SEED, glslInt(glsl, "ALBEDO_NOISE_OCTAVE_SEED"));
        assertEquals(Noise.FBM_GAIN, glslFloat(glsl, "ALBEDO_NOISE_FBM_GAIN"));
        assertEquals(Noise.RIDGED_LACUNARITY, glslFloat(glsl, "ALBEDO_NOISE_RIDGED_LACUNARITY"));
        assertEquals(Noise.RIDGED_SEED, glslInt(glsl, "ALBEDO_NOISE_RIDGED_SEED"));
    }

    @Test
    void theCoarseAndTheFineOctavesAddUpToTheFbm() {
        float coarse = Noise.fbmPart(0.3f, 1.7f, -2.2f, 5, 0, 3, 6), fine = Noise.fbmPart(0.3f, 1.7f, -2.2f, 5, 3, 6, 6);
        assertEquals(Noise.fbm(0.3f, 1.7f, -2.2f, 5, 6), coarse + fine, 1e-6f);
    }
}
