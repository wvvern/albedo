package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
        assertThrows(IOException.class, () -> SkyShaderSource.readInclude("minecraft:projection.glsl"));
    }

    @Test
    void inlineReplacesEachIncludeLineAlsoInAnIncludeAndKeepsTheLineNumbers() throws IOException {
        Map<String, String> files = new HashMap<String, String>();
        files.put("a:outer.glsl", "outer\n#include <a:inner.glsl>\n");
        files.put("a:inner.glsl", "inner\n");
        String text = SkyShaderSource.inline("#version 330\n  #include <a:outer.glsl> // note\n# include \"a:inner.glsl\"\nmain\n", files::get);
        assertEquals("#version 330\nouter\ninner\n#line 3\n#line 3\ninner\n#line 4\nmain\n", text);
    }

    @Test
    void aMissingIncludeOrALoopOfIncludesIsAnError() {
        Map<String, String> files = new HashMap<String, String>();
        files.put("a:loop.glsl", "#include <a:loop.glsl>\n");
        assertThrows(IOException.class, () -> SkyShaderSource.inline("#include <a:none.glsl>\n", files::get));
        assertThrows(IOException.class, () -> SkyShaderSource.inline("#include <a:loop.glsl>\n", files::get));
    }

    @Test
    void eachSharpShaderOf26HasNoIncludeLineAfterInline() throws IOException {
        Map<String, String> game = new HashMap<String, String>();
        game.put("minecraft:dynamictransforms.glsl", "uniform DynamicTransforms;\n");
        game.put("minecraft:projection.glsl", "uniform Projection;\n");
        for (String path : new String[] {"core/sky_sharp.vsh", "core/sky_sharp.fsh", "core/sky_equirect.vsh", "core/sky_equirect.fsh"}) {
            String text = SkyShaderSource.inline(SkyShaderSource.read(path),
                    id -> game.containsKey(id) ? game.get(id) : SkyShaderSource.readInclude(id));
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
