package dev.albedo.modern;

//? if device_chroma && !renderpearl {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import dev.albedo.core.Albedo;
import dev.albedo.core.shader.ShaderState;
import dev.albedo.core.sky.SkyShaderSource;
import dev.albedo.core.texture.ChromaFrames;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

// The Chroma pass from 1.21.11 to 26.2: a render pass of its own into each mip level of an atlas, with a pipeline that the game
// compiles from the complete shader text of the mod.
final class PrecompiledChromaPass implements ChromaBackend, DeviceChromaTarget.Pipelines {
    // One triangle over the whole mip level with no blend and no depth, so that each texel of the pass replaces the atlas texel.
    private static final RenderPipeline PIPELINE = DeviceApi.triangles(DeviceApi.sampler(DeviceApi.sampler(
            DeviceApi.uniformBlock(DeviceApi.colorOnly(RenderPipeline.builder()), "AlbedoChroma"), "AlbedoPristine"), "AlbedoTexels")
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/chroma"))
            .withVertexShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, SkyShaderSource.CHROMA))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, SkyShaderSource.CHROMA))
            .withCull(false), null)
            .build();

    // Copies mip levels above 0; see DeviceChromaTarget.copy().
    private static final RenderPipeline COPY = DeviceApi.triangles(DeviceApi.sampler(DeviceApi.colorOnly(RenderPipeline.builder()), "AlbedoSource")
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/chroma_copy"))
            .withVertexShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, SkyShaderSource.CHROMA))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, SkyShaderSource.CHROMA_COPY))
            .withCull(false), null)
            .build();

    private static final Precompiled PIPELINE_SHADER = new Precompiled(PIPELINE, SkyShaderSource.CHROMA, SkyShaderSource.CHROMA, "",
            "Chroma shader", "Chroma is off.");
    private static final Precompiled COPY_SHADER = new Precompiled(COPY, SkyShaderSource.CHROMA, SkyShaderSource.CHROMA_COPY, "",
            "Chroma copy shader", "Chroma is off.");

    @Override
    public ShaderState compile() {
        ShaderState chroma = PIPELINE_SHADER.compile();
        return chroma.usable() ? COPY_SHADER.compile() : chroma;
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
        pass.setPipeline(PIPELINE);
    }

    @Override
    public void copy(RenderPass pass) {
        pass.setPipeline(COPY);
    }
}
*///?}
