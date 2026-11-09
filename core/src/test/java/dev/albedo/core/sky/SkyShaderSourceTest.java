package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SkyShaderSourceTest {
    @Test
    void eachClassicShaderIsOneTextWithItsVersionFirst() throws IOException {
        for (SkyShaderSource.Dialect dialect : SkyShaderSource.Dialect.values()) {
            for (String name : new String[] {SkyShaderSource.SHARP, SkyShaderSource.EQUIRECT}) {
                for (boolean vertex : new boolean[] {true, false}) {
                    String text = SkyShaderSource.classic(name, vertex, dialect);
                    String what = dialect + " " + name + (vertex ? ".vsh" : ".fsh");
                    assertTrue(text.startsWith("#version "), what);
                    assertEquals(1, count(text, "#version"), what);
                    assertFalse(text.contains("#include"), what + " has no include step");
                    assertEquals(!vertex, text.contains("vec3 albedoSky(vec3 dir, float footprint)"), what);
                }
            }
        }
    }

    @Test
    void theDialectsSelectTheirUniformsAndMatrices() {
        assertTrue(SkyShaderSource.Dialect.BLOCKS_330.header().contains("ALBEDO_TRANSFORM_BLOCKS"));
        assertFalse(SkyShaderSource.Dialect.BLOCKS_330.header().contains("ALBEDO_PLAIN_UNIFORMS"));
        assertTrue(SkyShaderSource.Dialect.UNIFORMS_150.header().contains("ALBEDO_PLAIN_UNIFORMS"));
        assertTrue(SkyShaderSource.Dialect.FIXED_130.header().contains("ALBEDO_FIXED_FUNCTION"));
        assertTrue(SkyShaderSource.Dialect.FIXED_120_EXT.header().contains("GL_EXT_gpu_shader4"));
    }

    @Test
    void aMissingFileIsAnError() {
        assertThrows(IOException.class, () -> SkyShaderSource.read("core/none.fsh"));
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) n++;
        return n;
    }
}
