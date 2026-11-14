package dev.albedo.modern;

//? if renderpearl {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.albedo.core.Albedo;
import dev.albedo.core.sky.SkyDefer;
import dev.albedo.core.sky.SkyImage;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

/**
 * The custom sky of the versions with the shared sky pass: the sky shader of {@code SharpSky} when it can draw, else the
 * baked image on the sphere.
 */
public final class PearlSky {
    private static GpuBuffer sphere;
    private static int vertexCount;
    /** The transforms of the sharp sky of the sky pass, which waits for the solid world. */
    private static final SkyDefer<GpuBufferSlice> DEFER = new SkyDefer<>();
    // The texture with no blend, no depth test or write and no cull (the sky is the first thing in the frame), compiled on
    // the render thread at its first use, as a pipeline of the game is.
    private static final SharpSky.Pipeline PIPELINE = new SharpSky.Pipeline(RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/sky_layers"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Runnable::run, "sky texture shader", "The game draws its own sky.");

    private PearlSky() {}

    static void frameStart() {
        DEFER.frameStart();
    }

    private static GpuBuffer buildSphere() {
        float[] v = SkyImage.sphere(ModernSky.RADIUS);
        vertexCount = v.length / 5;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(vertexCount * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            for (int i = 0; i < v.length; i += 5) b.addVertex(v[i], v[i + 1], v[i + 2]).setUv(v[i + 3], v[i + 4]);
            try (MeshData mesh = b.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "Albedo sky sphere", 32, mesh.vertexBuffer());
            }
        }
    }

    /**
     * Uploads a new image before the sky pass of the overworld skybox opens, the only one that the custom sky replaces,
     * because a texture write is not allowed inside a pass.
     */
    public static void beforePass(boolean overworldSkybox) {
        SkyImage img = Albedo.skyImage();
        if (img == null || !overworldSkybox) return;
        ModernSky.upload(img);
        if (sphere == null) sphere = buildSphere();
        SharpSky.prepare(img);
    }

    /** Draws the sharp sky that waits, in the pass of the solid world, only on the pixels that the world left empty. */
    public static void afterSolid(RenderPass pass) {
        GpuBufferSlice waiting = DEFER.take();
        if (waiting == null) return;
        SharpSky.draw(pass, waiting, sphere, vertexCount);
    }

    /** Draws the last uploaded image in the shared sky pass, also when a newer one waits for the next frame, and returns false when there is no custom sky. */
    public static boolean draw(RenderPass pass) {
        SkyImage img = Albedo.skyImage();
        DynamicTexture texture = ModernSky.uploaded();
        if (img == null || texture == null || sphere == null) return false;
        float k = ModernSky.brightness(img);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(ModernSky.turned(RenderSystem.getModelViewMatrixCopy(), img), new Vector4f(k, k, k, 1f));
        if (SharpSky.ready()) {
            // The sharp sky adds its color to the frame, so the sky of this level view must be black below it.
            SharpSky.drawBlack(pass, transforms, sphere, vertexCount);
            // The sharp sky waits for the solid world, which then hides a part of it from the shader.
            if (!DEFER.defer(transforms)) SharpSky.draw(pass, transforms, sphere, vertexCount);
            return true;
        }
        CompiledRenderPipeline baked = PIPELINE.get();
        if (baked == null) return false;
        pass.pushDebugGroup(() -> "Albedo sky");
        pass.setPipeline(baked);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setUniform("Sampler0", texture.getTextureView(), RenderSystem.getSamplerCache().getSampler(
                AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false));
        pass.setVertexBuffer(0, sphere.slice());
        pass.draw(vertexCount, 1, 0, 0);
        pass.popDebugGroup();
        return true;
    }
}
//?}
