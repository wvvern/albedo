package dev.albedo.modern;

import dev.albedo.core.shader.ShaderState;
import dev.albedo.core.texture.ChromaTiming;
import net.minecraft.client.renderer.texture.TextureAtlas;

/** The GPU work of the Chroma pass on one version: the atlas textures that it turns, and a GPU timer for the harness. */
interface ChromaBackend {
    /** One atlas texture with its pristine copy and its Chroma texels on the GPU. */
    interface Target extends AutoCloseable {
        /** Whether the atlas still uses the GPU texture of this target, which a resource reload replaces. */
        boolean sameTexture(TextureAtlas atlas);

        /** Sends the Chroma texels of each mip level, as {@code ChromaTexels} gives them. */
        void texels(int[][] levels);

        /**
         * Copies each mip level of the atlas into the pristine copy, then draws the pristine copy with the hue of each
         * moving texel turned into the atlas.
         *
         * @param turns the part of one turn of each speed, from {@code Chroma.turns}
         */
        void recolor(float[] turns);

        /** Copies the pristine copy back into each mip level of the atlas, so that Albedo and the game write into the colors they painted. */
        void restore();

        /** The bytes of GPU memory of the pristine copy and the texels. */
        long bytes();

        @Override
        void close();
    }

    /** A GPU timer of this version for the harness timing. */
    interface Timer extends ChromaTiming.GpuTimer {
        /** The {@link #nanos} of a timer that the GPU cannot run. */
        long UNAVAILABLE = 0;
    }

    /**
     * Compiles the shader of the pass on the render thread when it is not compiled, or gives it to the game again when a
     * version needs that after a resource reload, which the pass calls in each frame before it draws.
     *
     * @return READY, RUNNING while the compile runs, or FAILED
     */
    ShaderState compile();

    /** The width, height and number of mip levels of the GPU texture of an atlas, or null before the atlas has its texture. */
    int[] size(TextureAtlas atlas);

    Target create(TextureAtlas atlas, int[] size);

    Timer timer();
}
