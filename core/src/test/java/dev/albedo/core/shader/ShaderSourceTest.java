package dev.albedo.core.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShaderSourceTest {
    @Test
    void theDialectsSelectTheirUniformsAndMatrices() {
        assertTrue(ShaderSource.Dialect.BLOCKS_330.header().contains("ALBEDO_TRANSFORM_BLOCKS"));
        assertFalse(ShaderSource.Dialect.BLOCKS_330.header().contains("ALBEDO_PLAIN_UNIFORMS"));
        assertTrue(ShaderSource.Dialect.UNIFORMS_150.header().contains("ALBEDO_PLAIN_UNIFORMS"));
        assertTrue(ShaderSource.Dialect.FIXED_130.header().contains("ALBEDO_FIXED_FUNCTION"));
        assertTrue(ShaderSource.Dialect.FIXED_120_EXT.header().contains("GL_EXT_gpu_shader4"));
    }

    @Test
    void theSharpSkyTakesGlsl130ElseTheExtensionWithHalfFloatsElseNone() {
        assertEquals(ShaderSource.Dialect.FIXED_130, ShaderSource.Dialect.sharp(true, false, false, false));
        assertEquals(ShaderSource.Dialect.FIXED_120_EXT, ShaderSource.Dialect.sharp(false, true, true, true));
        assertNull(ShaderSource.Dialect.sharp(false, false, true, true), "no EXT_gpu_shader4");
        assertNull(ShaderSource.Dialect.sharp(false, true, false, true), "no float textures");
        assertNull(ShaderSource.Dialect.sharp(false, true, true, false), "no half float pixels");
    }

    @Test
    void theChromaPassTakesGlsl130ElseGlsl120() {
        assertEquals(ShaderSource.Dialect.FIXED_130, ShaderSource.Dialect.chroma(true));
        assertEquals(ShaderSource.Dialect.FIXED_120, ShaderSource.Dialect.chroma(false));
    }

    @Test
    void aMissingFileIsAnError() {
        assertThrows(IOException.class, () -> ShaderSource.read("core/none.fsh"));
        assertThrows(IOException.class, () -> ShaderSource.readInclude("minecraft:projection.glsl"));
    }

    @Test
    void inlineReplacesEachIncludeLineAlsoInAnIncludeAndKeepsTheLineNumbers() throws IOException {
        Map<String, String> files = new HashMap<String, String>();
        files.put("a:outer.glsl", "outer\n#include <a:inner.glsl>\n");
        files.put("a:inner.glsl", "inner\n");
        String text = ShaderSource.inline("#version 330\n  #include <a:outer.glsl> // note\n# include \"a:inner.glsl\"\nmain\n", files::get);
        assertEquals("#version 330\nouter\ninner\n#line 3\n#line 3\ninner\n#line 4\nmain\n", text);
    }

    @Test
    void aMissingIncludeOrALoopOfIncludesIsAnError() {
        Map<String, String> files = new HashMap<String, String>();
        files.put("a:loop.glsl", "#include <a:loop.glsl>\n");
        assertThrows(IOException.class, () -> ShaderSource.inline("#include <a:none.glsl>\n", files::get));
        assertThrows(IOException.class, () -> ShaderSource.inline("#include <a:loop.glsl>\n", files::get));
    }
}
