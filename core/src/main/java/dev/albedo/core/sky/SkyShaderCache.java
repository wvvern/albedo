package dev.albedo.core.sky;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The sky shaders of the last skies by their {@link SkyProgram#shaderSteps}, so that a sky that comes back in the editor
 * does not compile again, where the oldest shader goes when the cache is full.
 *
 * @param <T> the shader of an adapter
 */
public final class SkyShaderCache<T> {
    private final int size;
    private final Function<String, T> create;
    private final Consumer<T> close;
    private final Map<String, T> shaders = new LinkedHashMap<String, T>(8, 0.75f, true);

    /**
     * @param size the number of shaders to keep
     * @param create makes a shader for layer steps, which compiles at its first use
     * @param close frees a shader that goes out of the cache
     */
    public SkyShaderCache(int size, Function<String, T> create, Consumer<T> close) {
        this.size = size;
        this.create = create;
        this.close = close;
    }

    /** The shader of these layer steps, made now when the cache does not have it. */
    public T get(String steps) {
        T shader = shaders.get(steps);
        if (shader != null) return shader;
        shader = create.apply(steps);
        shaders.put(steps, shader);
        Iterator<T> oldest = shaders.values().iterator();
        while (shaders.size() > size) {
            close.accept(oldest.next());
            oldest.remove();
        }
        return shader;
    }
}
