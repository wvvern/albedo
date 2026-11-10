package dev.albedo.modern;

//? if >=1.21.11 && <26.2 {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.albedo.core.Albedo;
import dev.albedo.core.sky.SkyDefer;
import dev.albedo.core.sky.SkyImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

// The custom sky of the versions with a GPU device and a render pass for each draw: the sky shader of PrecompiledSharpSky
// when it can draw, else the baked image on the sphere.
public final class PrecompiledSky {
    private static GpuBuffer sphere;
    private static int vertexCount;
    // The transforms of the sharp sky of the sky pass, which waits for the solid world.
    private static final SkyDefer<GpuBufferSlice> DEFER = new SkyDefer<>();
    // The texture with no blend, no depth test or write and no cull: the sky is the first thing in the frame.
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/sky_layers"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withSampler("Sampler0")
            .withoutBlend()
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES)
            .build();

    private PrecompiledSky() {}

    // Starts a frame, which forgets the sharp sky of the last frame that still waits.
    static void frameStart() {
        DEFER.frameStart();
    }

    private static GpuBuffer buildSphere() {
        float[] v = SkyImage.sphere(ModernSky.RADIUS);
        vertexCount = v.length / 5;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(vertexCount * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            for (int i = 0; i < v.length; i += 5) b.addVertex(v[i], v[i + 1], v[i + 2]).setUv(v[i + 3], v[i + 4]);
            try (MeshData mesh = b.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "Albedo sky sphere", 32, mesh.vertexBuffer());
            }
        }
    }

    // A render pass that draws into the color and the depth of the frame.
    private static RenderPass pass() {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Albedo sky", target.getColorTextureView(),
                OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty());
    }

    // Draws the sky in its own render pass in place of the vanilla disc, and returns false when there is no custom sky.
    public static boolean draw() {
        SkyImage img = Albedo.skyImage();
        if (img == null) return false;
        DynamicTexture texture = ModernSky.upload(img);
        if (sphere == null) sphere = buildSphere();
        boolean sharp = PrecompiledSharpSky.prepare(img);
        float k = ModernSky.brightness(img);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(ModernSky.turned(RenderSystem.getModelViewMatrix(), img),
                new Vector4f(k, k, k, 1f), new Vector3f(), new Matrix4f());
        try (RenderPass pass = pass()) {
            if (sharp) {
                // The sharp sky adds its color to the frame, so the sky of this level view must be black below it.
                PrecompiledSharpSky.drawBlack(pass, transforms, sphere, vertexCount);
                // The sharp sky waits for the solid world, which then hides a part of it from the shader.
                if (!DEFER.defer(transforms)) PrecompiledSharpSky.draw(pass, transforms, sphere, vertexCount);
                return true;
            }
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", texture.getTextureView(), RenderSystem.getSamplerCache().getSampler(
                    AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false));
            pass.setVertexBuffer(0, sphere);
            pass.draw(0, vertexCount);
        }
        return true;
    }

    // Draws the sharp sky that waits, after the solid world, only on the pixels that the world left empty.
    public static void afterSolid() {
        GpuBufferSlice waiting = DEFER.take();
        if (waiting == null) return;
        try (RenderPass pass = pass()) {
            PrecompiledSharpSky.draw(pass, waiting, sphere, vertexCount);
        }
    }
}
*///?}
