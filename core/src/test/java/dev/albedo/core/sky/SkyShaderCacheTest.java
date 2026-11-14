package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SkyShaderCacheTest {
    @Test
    void theShaderCacheKeepsTheLastShadersAndClosesTheOldest() {
        List<String> closed = new ArrayList<String>();
        SkyShaderCache<String> cache = new SkyShaderCache<String>(2, steps -> "shader " + steps, closed::add);
        String a = cache.get("a");
        assertSame(a, cache.get("a"));
        cache.get("b");
        cache.get("a");
        cache.get("c");
        assertEquals(Arrays.asList("shader b"), closed, "the least recently used shader goes");
        assertSame(a, cache.get("a"));
    }
}
