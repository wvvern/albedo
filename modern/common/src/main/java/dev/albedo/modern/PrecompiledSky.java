package dev.albedo.modern;

//? if >=1.21.6 && <26.3 {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.albedo.core.Albedo;
import dev.albedo.core.sky.SkyDefer;
import dev.albedo.core.sky.SkyImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Vector4f;

// The custom sky of the versions with a GPU device and a render pass for each draw: the sky shader of PrecompiledSharpSky
// when it can draw, else the baked image on the sphere.
public final class PrecompiledSky {
    private static GpuBuffer sphere;
    private static int vertexCount;
    // The transforms of the sharp sky of the sky pass, which waits for the solid world.
    private static final SkyDefer<GpuBufferSlice> DEFER = new SkyDefer<>();
    // The texture with no blend, no depth test or write and no cull: the sky is the first thing in the frame.
    private static final RenderPipeline PIPELINE = DeviceApi.triangles(
            DeviceApi.sampler(DeviceApi.transformBlocks(DeviceApi.state(RenderPipeline.builder(), false, false)), "Sampler0")
                    .withLocation(DeviceApi.id("pipeline/sky_layers"))
                    .withVertexShader("core/position_tex")
                    .withFragmentShader("core/position_tex")
                    .withCull(false), DefaultVertexFormat.POSITION_TEX)
            .build();

    private PrecompiledSky() {}

    // Starts a frame, which forgets the sharp sky of the last frame that still waits.
    static void frameStart() {
        DEFER.frameStart();
    }

    private static GpuBuffer buildSphere() {
        float[] v = SkyImage.sphere(ModernSky.RADIUS);
        vertexCount = v.length / 5;
        return DeviceApi.vertexBuffer("Albedo sky sphere", v);
    }

    // A render pass that draws into the color and the depth of the frame.
    private static RenderPass pass() {
        RenderTarget target = DeviceApi.mainTarget();
        return DeviceApi.pass("Albedo sky", target.getColorTextureView(), target.getDepthTextureView());
    }

    // Draws the sky in its own render pass in place of the vanilla disc, and returns false when there is no custom sky.
    public static boolean draw() {
        SkyImage img = Albedo.skyImage();
        if (img == null) return false;
        DynamicTexture texture = ModernSky.upload(img);
        if (sphere == null) sphere = buildSphere();
        boolean sharp = PrecompiledSharpSky.prepare(img);
        float k = ModernSky.brightness(img);
        GpuBufferSlice transforms = DeviceApi.transforms(ModernSky.turned(DeviceApi.modelView(), img), new Vector4f(k, k, k, 1f));
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
            DeviceApi.bindSky(pass, "Sampler0", texture.getTextureView());
            DeviceApi.draw(pass, sphere, vertexCount);
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
