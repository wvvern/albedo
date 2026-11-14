package dev.albedo.modern;

//? if renderpearl {
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import dev.albedo.core.shader.ShaderState;
import dev.albedo.core.texture.ChromaFrames;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * The Chroma pass on 26.x: a render pass of its own into each mip level of an atlas, with a pipeline that compiles on the
 * render thread from the complete shader text of the mod.
 */
final class ChromaPass implements ChromaBackend, DeviceChromaTarget.Pipelines {
    /** The turn of each speed, the pristine atlas and the texels, which the fragment shader reads by the texel it draws. */
    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("AlbedoChroma", UniformType.UNIFORM_BUFFER)
            .withUniform("AlbedoPristine", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("AlbedoTexels", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    // One triangle over the whole mip level with no blend and no depth, which compiles on the render thread, because a
    // compile on a worker thread can stop the JVM on Windows (see SkyShaderSource.inline).
    private static final PearlPipeline PIPELINE = new PearlPipeline(RenderPipeline.builder()
            .withBindGroupLayout(LAYOUT)
            .withLocation(ModIds.id("pipeline/chroma"))
            .withVertexShader(ModIds.id("core/chroma"))
            .withFragmentShader(ModIds.id("core/chroma"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Runnable::run, "Chroma shader", "Chroma is off.");

    /** The mip level that the copy pipeline reads. */
    private static final BindGroupLayout COPY_LAYOUT = BindGroupLayout.builder()
            .withUniform("AlbedoSource", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    // Copies mip levels above 0; see DeviceChromaTarget.copy().
    private static final PearlPipeline COPY = new PearlPipeline(RenderPipeline.builder()
            .withBindGroupLayout(COPY_LAYOUT)
            .withLocation(ModIds.id("pipeline/chroma_copy"))
            .withVertexShader(ModIds.id("core/chroma"))
            .withFragmentShader(ModIds.id("core/chroma_copy"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Runnable::run, "Chroma copy shader", "Chroma is off.");

    @Override
    public ShaderState compile() {
        ShaderState chroma = PIPELINE.compile(), copy = COPY.compile();
        if (chroma.usable() && copy.usable()) return ShaderState.READY;
        return chroma.dead() || copy.dead() ? ShaderState.FAILED : ShaderState.RUNNING;
    }

    @Override
    public ChromaFrames.Size size(TextureAtlas atlas) {
        return DeviceChromaTarget.size(atlas);
    }

    @Override
    public ChromaBackend.Target create(TextureAtlas atlas, ChromaFrames.Size size) {
        return new DeviceChromaTarget(atlas.getTexture(), size.levels, this);
    }

    @Override
    public ChromaBackend.Timer timer() {
        return new DeviceChromaTarget.Timer();
    }

    @Override
    public void chroma(RenderPass pass) {
        pass.setPipeline(compiled(PIPELINE, "the Chroma shader is not compiled"));
    }

    @Override
    public void copy(RenderPass pass) {
        pass.setPipeline(compiled(COPY, "the Chroma copy shader is not compiled"));
    }

    private static CompiledRenderPipeline compiled(PearlPipeline pipeline, String missing) {
        CompiledRenderPipeline compiled = pipeline.get();
        if (compiled == null) throw new IllegalStateException(missing);
        return compiled;
    }
}
//?}
