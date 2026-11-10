package dev.albedo.modern;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.albedo.core.Albedo;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.dev.SkyCpuTime;
import dev.albedo.core.sky.SkyDayTime;
import dev.albedo.core.sky.SkyDefer;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyLight;
import dev.albedo.core.sky.SkyTurn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Draws the custom sky on a sphere in place of the vanilla sky disc, with the sky shader of each version when it can
 * ({@code SharpSky}, {@code PrecompiledSharpSky} or {@code GlSharpSky}), else from one texture of a fixed size that is
 * uploaded on the render thread only when the core has a new image.
 */
public final class ModernSky {
    private static final float RADIUS = 100f;
    private static DynamicTexture texture;
    private static long uploaded = -1;
    private static int vertexCount;
    private static java.lang.reflect.Method irisInUse;
    private static Object iris;
    private static boolean irisLooked;
    //? if >=26.1 {
    private static com.mojang.renderpearl.api.buffers.GpuBuffer sphere;
    /** The transforms of the sharp sky of the sky pass, which waits for the solid world. */
    private static final SkyDefer<com.mojang.renderpearl.api.buffers.GpuBufferSlice> DEFER = new SkyDefer<>();
    // The texture with no blend, no depth test or write and no cull (the sky is the first thing in the frame), compiled on
    // the render thread at its first use, as a pipeline of the game is.
    private static final SharpSky.Pipeline PIPELINE = new SharpSky.Pipeline(com.mojang.renderpearl.api.pipeline.RenderPipeline.builder()
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
            .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/sky_layers"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withColorTargetState(com.mojang.renderpearl.api.pipeline.ColorTargetState.DEFAULT)
            .withDepthStencilState(new com.mojang.renderpearl.api.pipeline.DepthStencilState(
                    com.mojang.renderpearl.api.pipeline.CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .build(), Runnable::run, "sky texture shader", "The game draws its own sky.");
    //?} elif >=1.21.11 {
    /*private static com.mojang.blaze3d.buffers.GpuBuffer sphere;
    // The transforms of the sharp sky of the sky pass, which waits for the solid world.
    private static final SkyDefer<com.mojang.blaze3d.buffers.GpuBufferSlice> DEFER = new SkyDefer<>();
    // The texture with no blend, no depth test or write and no cull: the sky is the first thing in the frame.
    private static final com.mojang.blaze3d.pipeline.RenderPipeline PIPELINE = com.mojang.blaze3d.pipeline.RenderPipeline.builder()
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/sky_layers"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withSampler("Sampler0")
            .withoutBlend()
            .withDepthWrite(false)
            .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES)
            .build();
    *///?} else {
    /*private static com.mojang.blaze3d.vertex.VertexBuffer sphere;
    // The model view matrix of the sharp sky of the sky pass, which waits for the solid world with its projection and brightness.
    private static final SkyDefer<Matrix4f> DEFER = new SkyDefer<>();
    private static Matrix4f waitingProjection;
    private static float waitingBrightness;
    *///?}

    private ModernSky() {}

    /** Starts a frame, which forgets the sharp sky of the last frame that still waits. */
    static void frameStart() {
        DEFER.frameStart();
    }

    /** True when the custom sky replaces the vanilla sky disc in this frame. */
    public static boolean active() {
        return Albedo.skyImage() != null;
    }

    /** True when the custom sky hides the vanilla stars. */
    public static boolean hidesStars() {
        SkyImage img = Albedo.skyImage();
        return img != null && img.design().celestial() == SkyDesign.Celestial.HIDE;
    }

    /** True when the image draws with the sky shader in this frame: the design and the setting permit it, and no shader pack draws the sky. */
    static boolean wantsSharp(SkyImage img) {
        return img.drawsSharp() && !shaderPackInUse();
    }

    /** Iris with a shader pack draws the sky in its own way, so the sky then draws from the baked texture. */
    private static boolean shaderPackInUse() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                iris = null;
            }
        }
        if (iris == null) return false;
        try {
            return (Boolean) irisInUse.invoke(iris);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** The factor of the sky and fog colors for the time of day and the weather, 1 for a sky that stays bright. */
    static float brightness(SkyImage img) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || img.design().light() != SkyDesign.Light.DAY_NIGHT) return 1f;
        return SkyLight.brightness(dayTime(level), level.getRainLevel(1f), level.getThunderLevel(1f));
    }

    /** The day time of the world in ticks with no wrap, where 0 is sunrise. */
    private static long dayTime(ClientLevel level) {
        //? if >=26.1 {
        return level.getOverworldClockTime();
        //?} else {
        /*return level.getDayTime();
        *///?}
    }

    /** Records the day time at the end of each client tick, for the frames until the next tick. */
    static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) SkyDayTime.tick(dayTime(level));
    }

    /** The rotation of the custom sky in this frame, for the sky sphere and the fog. */
    static SkyTurn turn(SkyImage img) {
        ClientLevel level = Minecraft.getInstance().level;
        return SkyTurn.at(img.design().turning(), level == null ? 0 : SkyDayTime.at(partialTick()));
    }

    /** The part of a tick from the last tick to this frame. */
    private static float partialTick() {
        //? if >=1.21.11 {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        //?} else {
        /*return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        *///?}
    }

    /** A copy of the model view matrix of the sky sphere, turned for a sky that turns. */
    static Matrix4f turned(Matrix4fc modelView, SkyImage img) {
        SkyTurn turn = turn(img);
        Matrix4f out = new Matrix4f(modelView);
        return turn.still() ? out : out.mul(new Matrix4f().set(turn.matrix()));
    }

    /** The camera yaw in degrees, where 0 looks south. */
    static float cameraYaw() {
        //? if >=26.1 {
        return Minecraft.getInstance().gameRenderer.mainCamera().yRot();
        //?} elif >=1.21.11 {
        /*return Minecraft.getInstance().gameRenderer.getMainCamera().yRot();
        *///?} else {
        /*return Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
        *///?}
    }

    /** Sends a new image to the texture and makes the sphere once; the render thread calls this. */
    private static void prepare(SkyImage img) {
        if (texture == null || texture.getPixels() == null || texture.getPixels().getWidth() != img.width()
                || texture.getPixels().getHeight() != img.height()) {
            if (texture != null) texture.close();
            NativeImage pixels = new NativeImage(img.width(), img.height(), false);
            //? if >=1.21.11 {
            texture = new DynamicTexture(() -> "Albedo sky", pixels);
            //?} else {
            /*texture = new DynamicTexture(pixels);
            *///?}
            uploaded = -1;
        }
        if (uploaded != img.serial()) {
            NativeImagePixels out = new NativeImagePixels(texture.getPixels());
            int[] argb = img.argb();
            for (int y = 0; y < img.height(); y++) {
                for (int x = 0; x < img.width(); x++) out.setArgb(x, y, argb[y * img.width() + x]);
            }
            texture.upload();
            //? if <1.21.11 {
            /*// A dynamic texture uploads with nearest filtering and repeat in both directions.
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(texture.getId());
            com.mojang.blaze3d.platform.GlStateManager._texParameter(3553, 10241, 9729);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(3553, 10240, 9729);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(3553, 10242, 10497);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(3553, 10243, 33071);
            *///?}
            uploaded = img.serial();
        }
        if (sphere == null) sphere = buildSphere();
    }

    //? if >=26.1 {
    private static com.mojang.renderpearl.api.buffers.GpuBuffer buildSphere() {
        float[] v = SkyImage.sphere(RADIUS);
        vertexCount = v.length / 5;
        try (com.mojang.blaze3d.vertex.ByteBufferBuilder bytes = com.mojang.blaze3d.vertex.ByteBufferBuilder.exactlySized(
                vertexCount * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            for (int i = 0; i < v.length; i += 5) b.addVertex(v[i], v[i + 1], v[i + 2]).setUv(v[i + 3], v[i + 4]);
            try (com.mojang.blaze3d.vertex.MeshData mesh = b.buildOrThrow()) {
                return com.mojang.blaze3d.systems.RenderSystem.getDevice().createBuffer(() -> "Albedo sky sphere", 32, mesh.vertexBuffer());
            }
        }
    }

    // Uploads a new image before the sky pass of the overworld skybox opens, the only one that the custom sky replaces, because a
    // texture write is not allowed inside a pass.
    public static void beforePass(boolean overworldSkybox) {
        SkyImage img = Albedo.skyImage();
        if (img == null || !overworldSkybox) return;
        long start = SkyCpuTime.start();
        prepare(img);
        SharpSky.prepare(img);
        SkyCpuTime.add(start);
    }

    // Draws the sharp sky that waits, in the pass of the solid world, only on the pixels that the world left empty.
    public static void afterSolid(com.mojang.renderpearl.api.commands.RenderPass pass) {
        com.mojang.renderpearl.api.buffers.GpuBufferSlice waiting = DEFER.take();
        if (waiting == null) return;
        long start = SkyCpuTime.start();
        SharpSky.draw(pass, waiting, sphere, vertexCount);
        SkyCpuTime.add(start);
    }

    // Draws the last uploaded image in the shared sky pass, also when a newer one waits for the next frame, and returns false when there is no custom sky.
    public static boolean draw(com.mojang.renderpearl.api.commands.RenderPass pass) {
        long start = SkyCpuTime.start();
        try {
            return drawInPass(pass);
        } finally {
            SkyCpuTime.add(start);
        }
    }

    private static boolean drawInPass(com.mojang.renderpearl.api.commands.RenderPass pass) {
        SkyImage img = Albedo.skyImage();
        if (img == null || texture == null || sphere == null || uploaded < 0) return false;
        float k = brightness(img);
        com.mojang.renderpearl.api.buffers.GpuBufferSlice transforms = com.mojang.blaze3d.systems.RenderSystem.getDynamicUniforms()
                .writeTransform(turned(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy(), img), new org.joml.Vector4f(k, k, k, 1f));
        if (SharpSky.ready()) {
            // The sharp sky adds its color to the frame, so the sky of this level view must be black below it.
            SharpSky.drawBlack(pass, transforms, sphere, vertexCount);
            // The sharp sky waits for the solid world, which then hides a part of it from the shader.
            if (!DEFER.defer(transforms)) SharpSky.draw(pass, transforms, sphere, vertexCount);
            return true;
        }
        com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline baked = PIPELINE.get();
        if (baked == null) return false;
        pass.pushDebugGroup(() -> "Albedo sky");
        pass.setPipeline(baked);
        com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setUniform("Sampler0", texture.getTextureView(), com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getSampler(
                com.mojang.renderpearl.api.textures.AddressMode.REPEAT, com.mojang.renderpearl.api.textures.AddressMode.CLAMP_TO_EDGE,
                com.mojang.renderpearl.api.textures.FilterMode.LINEAR, com.mojang.renderpearl.api.textures.FilterMode.LINEAR, false));
        pass.setVertexBuffer(0, sphere.slice());
        pass.draw(vertexCount, 1, 0, 0);
        pass.popDebugGroup();
        return true;
    }
    //?} elif >=1.21.11 {
    /*private static com.mojang.blaze3d.buffers.GpuBuffer buildSphere() {
        float[] v = SkyImage.sphere(RADIUS);
        vertexCount = v.length / 5;
        try (com.mojang.blaze3d.vertex.ByteBufferBuilder bytes = com.mojang.blaze3d.vertex.ByteBufferBuilder.exactlySized(
                vertexCount * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            for (int i = 0; i < v.length; i += 5) b.addVertex(v[i], v[i + 1], v[i + 2]).setUv(v[i + 3], v[i + 4]);
            try (com.mojang.blaze3d.vertex.MeshData mesh = b.buildOrThrow()) {
                return com.mojang.blaze3d.systems.RenderSystem.getDevice().createBuffer(() -> "Albedo sky sphere", 32, mesh.vertexBuffer());
            }
        }
    }

    // Draws the sky in its own render pass in place of the vanilla disc, and returns false when there is no custom sky.
    public static boolean draw() {
        long start = SkyCpuTime.start();
        try {
            return drawPass();
        } finally {
            SkyCpuTime.add(start);
        }
    }

    private static boolean drawPass() {
        SkyImage img = Albedo.skyImage();
        if (img == null) return false;
        prepare(img);
        boolean sharp = PrecompiledSharpSky.prepare(img);
        float k = brightness(img);
        com.mojang.blaze3d.buffers.GpuBufferSlice transforms = com.mojang.blaze3d.systems.RenderSystem.getDynamicUniforms()
                .writeTransform(turned(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix(), img), new org.joml.Vector4f(k, k, k, 1f),
                        new org.joml.Vector3f(), new org.joml.Matrix4f());
        com.mojang.blaze3d.pipeline.RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        try (com.mojang.blaze3d.systems.RenderPass pass = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Albedo sky", target.getColorTextureView(), java.util.OptionalInt.empty(),
                        target.getDepthTextureView(), java.util.OptionalDouble.empty())) {
            if (sharp) {
                // The sharp sky adds its color to the frame, so the sky of this level view must be black below it.
                PrecompiledSharpSky.drawBlack(pass, transforms, sphere, vertexCount);
                // The sharp sky waits for the solid world, which then hides a part of it from the shader.
                if (!DEFER.defer(transforms)) PrecompiledSharpSky.draw(pass, transforms, sphere, vertexCount);
                return true;
            }
            pass.setPipeline(PIPELINE);
            com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", texture.getTextureView(), com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getSampler(
                    com.mojang.blaze3d.textures.AddressMode.REPEAT, com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                    com.mojang.blaze3d.textures.FilterMode.LINEAR, com.mojang.blaze3d.textures.FilterMode.LINEAR, false));
            pass.setVertexBuffer(0, sphere);
            pass.draw(0, vertexCount);
        }
        return true;
    }

    // Draws the sharp sky that waits, after the solid world, only on the pixels that the world left empty.
    public static void afterSolid() {
        com.mojang.blaze3d.buffers.GpuBufferSlice waiting = DEFER.take();
        if (waiting == null) return;
        long start = SkyCpuTime.start();
        com.mojang.blaze3d.pipeline.RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        try (com.mojang.blaze3d.systems.RenderPass pass = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Albedo sky", target.getColorTextureView(), java.util.OptionalInt.empty(),
                        target.getDepthTextureView(), java.util.OptionalDouble.empty())) {
            PrecompiledSharpSky.draw(pass, waiting, sphere, vertexCount);
        }
        SkyCpuTime.add(start);
    }
    *///?} else {
    /*private static com.mojang.blaze3d.vertex.VertexBuffer buildSphere() {
        float[] v = SkyImage.sphere(RADIUS);
        vertexCount = v.length / 5;
        BufferBuilder b = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < v.length; i += 5) b.addVertex(v[i], v[i + 1], v[i + 2]).setUv(v[i + 3], v[i + 4]);
        com.mojang.blaze3d.vertex.VertexBuffer buffer = new com.mojang.blaze3d.vertex.VertexBuffer(
                com.mojang.blaze3d.vertex.VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(b.buildOrThrow());
        com.mojang.blaze3d.vertex.VertexBuffer.unbind();
        return buffer;
    }

    // Draws the sky with the matrices of the vanilla disc, and returns false when there is no custom sky.
    public static boolean draw(org.joml.Matrix4f skyModelView, org.joml.Matrix4f projection) {
        long start = SkyCpuTime.start();
        try {
            return drawGl(skyModelView, projection);
        } finally {
            SkyCpuTime.add(start);
        }
    }

    private static boolean drawGl(org.joml.Matrix4f skyModelView, org.joml.Matrix4f projection) {
        SkyImage img = Albedo.skyImage();
        if (img == null) return false;
        prepare(img);
        float k = brightness(img);
        Matrix4f modelView = turned(skyModelView, img);
        if (GlSharpSky.prepare(img)) {
            // The sharp sky adds its color to the frame, so the sky of this level view must be black below it.
            GlSharpSky.drawBlack(sphere, texture.getId(), modelView, projection);
            // The sharp sky waits for the solid world, which then hides a part of it from the shader.
            if (DEFER.defer(modelView)) {
                waitingProjection = new Matrix4f(projection);
                waitingBrightness = k;
            } else {
                GlSharpSky.draw(sphere, modelView, projection, k);
            }
            return true;
        }
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture.getId());
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(k, k, k, 1f);
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        sphere.bind();
        sphere.drawWithShader(modelView, projection, com.mojang.blaze3d.systems.RenderSystem.getShader());
        com.mojang.blaze3d.vertex.VertexBuffer.unbind();
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        return true;
    }

    // Draws the sharp sky that waits, after the solid world, only on the pixels that the world left empty, and leaves the depth
    // state of the end of the cutout layer.
    public static void afterSolid() {
        Matrix4f waiting = DEFER.take();
        if (waiting == null) return;
        long start = SkyCpuTime.start();
        GlSharpSky.draw(sphere, waiting, waitingProjection, waitingBrightness);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        SkyCpuTime.add(start);
    }
    *///?}
}
