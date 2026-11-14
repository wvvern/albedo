package dev.albedo.modern;

//? if device_sky {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.albedo.core.Albedo;
import dev.albedo.core.shader.ShaderSource;
import dev.albedo.core.shader.ShaderState;
import dev.albedo.core.sky.SkyLayerSteps;

import java.io.IOException;

// A pipeline of the mod that the game compiles from the complete classic shader text of the mod, because the game does not
// load the shaders of a mod as resources.
final class Precompiled {
    final RenderPipeline pipeline;
    private final String vertex;
    private final String fragment;
    private final String steps;
    private final String what;
    private final String fallback;
    private ShaderState state = ShaderState.NOT_STARTED;

    // A pipeline from the vertex shader of one file and the fragment shader of an other, where what and fallback are the words of the log lines.
    Precompiled(RenderPipeline pipeline, String vertex, String fragment, String steps, String what, String fallback) {
        this.pipeline = pipeline;
        this.vertex = vertex;
        this.fragment = fragment;
        this.steps = steps;
        this.what = what;
        this.fallback = fallback;
    }

    // Gives the pipeline to the game and returns READY or FAILED, where a failed pipeline never compiles again.
    ShaderState compile() {
        if (state.dead()) return state;
        // A resource reload empties the pipeline cache, so the pipeline goes to the game in each frame before a pass uses it.
        if (RenderSystem.getDevice().precompilePipeline(pipeline, (id, type) -> source(type)).isValid()) {
            if (state == ShaderState.NOT_STARTED) Albedo.get().platform().info("Compiled the " + what + " " + pipeline.getLocation());
            state = state.to(ShaderState.READY);
            return state;
        }
        state = state.to(ShaderState.FAILED);
        Albedo.get().platform().warn("Cannot compile the " + what + " " + pipeline.getLocation() + ". " + fallback, null);
        return state;
    }

    // Compiles the pipeline as compile() does, and returns true when it is ready.
    boolean ready() {
        return compile().usable();
    }

    // The complete text of a shader of the pipeline, with its includes and the layer steps in it, or null when it cannot be read.
    private String source(ShaderType type) {
        boolean isVertex = type == ShaderType.VERTEX;
        String file = isVertex ? vertex : fragment;
        try {
            return ShaderSource.classic(file, isVertex, DeviceApi.dialect(), SkyLayerSteps.includes(steps));
        } catch (IOException e) {
            Albedo.get().platform().warn("Cannot read the " + what + " " + file, e);
            return null;
        }
    }
}
*///?}
