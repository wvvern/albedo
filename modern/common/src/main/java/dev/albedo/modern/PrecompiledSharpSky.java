package dev.albedo.modern;

//? if >=1.21.11 && <26.1 {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuQuery;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.albedo.core.Albedo;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyShaderSource;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import java.util.OptionalLong;

// Draws the custom sky with the sky shader for each screen pixel on 1.21.11, so that it is sharp at each resolution and
// field of view, from pipelines that the game compiles from the complete shader text of the mod.
final class PrecompiledSharpSky {
    // The state of the compile of one pipeline.
    private enum Compile { NOT_STARTED, READY, FAILED }

    // A pipeline whose shaders come from the jar of the mod, because the game does not load the shaders of a mod as resources.
    private static final class Pipeline {
        final RenderPipeline pipeline;
        Compile state = Compile.NOT_STARTED;

        Pipeline(RenderPipeline pipeline) {
            this.pipeline = pipeline;
        }

        // Gives the pipeline to the game before each use, because a resource reload empties the pipeline cache, and returns true when it compiled.
        boolean ready() {
            if (state == Compile.FAILED) return false;
            if (RenderSystem.getDevice().precompilePipeline(pipeline, PrecompiledSharpSky::source).isValid()) {
                if (state == Compile.NOT_STARTED) Albedo.get().platform().info("Compiled the sharp sky shader " + pipeline.getLocation());
                state = Compile.READY;
                return true;
            }
            state = Compile.FAILED;
            Albedo.get().platform().warn("Cannot compile the sharp sky shader " + pipeline.getLocation() + ". The sky draws from the baked image.", null);
            return false;
        }
    }

    // The term images of a sky as one half float texture with the images one under the other.
    private static final class Terms implements AutoCloseable {
        final GpuTexture texture;
        final GpuTextureView view;

        private Terms(GpuTexture texture, GpuTextureView view) {
            this.texture = texture;
            this.view = view;
        }

        // Sends the term images of a sky to a new texture, which has one texel of 0 when the sky has no terms.
        static Terms upload(SkyImage img, String label) {
            int width = img.termTextureWidth(), height = img.termTextureHeight();
            GpuTexture texture = RenderSystem.getDevice().createTexture(() -> label, GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8,
                    width, height, 1, 1);
            // The game has no half float format, so the GL gets the level again in half floats.
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(((GlTexture) texture).glId());
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
            ByteBuffer bytes = MemoryUtil.memCalloc(width * height * 8);
            try {
                bytes.asShortBuffer().put(img.terms());
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL30.GL_HALF_FLOAT, bytes);
            } finally {
                MemoryUtil.memFree(bytes);
            }
            return new Terms(texture, RenderSystem.getDevice().createTextureView(texture));
        }

        // Binds the texture to the sampler of the sky shader, with linear filters, and with wrap only around the sky.
        void bind(RenderPass pass) {
            pass.bindTexture("AlbedoTerms", view, RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, false));
        }

        @Override
        public void close() {
            view.close();
            texture.close();
        }
    }

    // The sky on the sphere with no blend, no depth test or write and no cull: the sky is the first thing in the frame.
    private static final Pipeline SHARP = new Pipeline(RenderPipeline.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("AlbedoSky", UniformType.UNIFORM_BUFFER)
            .withSampler("AlbedoTerms")
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/sky_sharp"))
            .withVertexShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, SkyShaderSource.SHARP))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, SkyShaderSource.SHARP))
            .withoutBlend()
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES)
            .build());

    // The sky into the equirectangular image of the CPU bake, for the harness.
    private static final Pipeline EQUIRECT = new Pipeline(RenderPipeline.builder()
            .withUniform("AlbedoSky", UniformType.UNIFORM_BUFFER)
            .withSampler("AlbedoTerms")
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/sky_equirect"))
            .withVertexShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, SkyShaderSource.EQUIRECT))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, SkyShaderSource.EQUIRECT))
            .withoutBlend()
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES)
            .build());

    private static GpuBuffer block;
    private static Terms terms;
    private static long blockSerial = -1;

    private PrecompiledSharpSky() {}

    // The complete text of a shader of the mod, with the sky include in it.
    private static String source(Identifier id, ShaderType type) {
        try {
            return SkyShaderSource.classic(id.getPath(), type == ShaderType.VERTEX, SkyShaderSource.Dialect.BLOCKS_330);
        } catch (IOException e) {
            Albedo.get().platform().warn("Cannot read the sky shader " + id, e);
            return null;
        }
    }

    // Compiles the pipeline and sends the numbers and the term images of a new image to the GPU, before the sky pass opens.
    static void prepare(SkyImage img) {
        if (!ModernSky.wantsSharp(img) || !SHARP.ready() || blockSerial == img.serial()) return;
        if (block != null) block.close();
        if (terms != null) terms.close();
        block = upload(img.program(), "Albedo sky block");
        terms = Terms.upload(img, "Albedo sky terms");
        blockSerial = img.serial();
    }

    // Draws the image sharp in the sky pass and returns true, or returns false when the baked texture must draw it.
    static boolean draw(RenderPass pass, SkyImage img, GpuBufferSlice transforms, GpuBuffer sphere, int vertexCount) {
        if (!ModernSky.wantsSharp(img) || SHARP.state != Compile.READY || blockSerial != img.serial()) return false;
        pass.setPipeline(SHARP.pipeline);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setUniform("AlbedoSky", block);
        terms.bind(pass);
        pass.setVertexBuffer(0, sphere);
        pass.draw(0, vertexCount);
        return true;
    }

    // A uniform buffer with the block of a program.
    private static GpuBuffer upload(SkyProgram program, String label) {
        float[] values = program.block();
        ByteBuffer bytes = MemoryUtil.memAlloc(values.length * 4);
        try {
            bytes.asFloatBuffer().put(values);
            return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM, bytes);
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    // Draws the program and the term images of a sky repeat times into an equirectangular image of this size, then gives
    // the pixels and the GPU time of one draw, and returns false when the shader cannot compile; png may be null.
    static boolean drawEquirect(SkyImage sky, int width, int height, int repeat, File png, Harness.SkyDone done) {
        if (!EQUIRECT.ready()) return false;
        TextureTarget target = new TextureTarget("Albedo sky compare", width, height, false);
        GpuBuffer uniforms = upload(sky.program(), "Albedo sky compare block");
        Terms skyTerms = Terms.upload(sky, "Albedo sky compare terms");
        GpuBuffer quad = quad();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuQuery timer = null;
        try {
            try {
                timer = encoder.timerQueryBegin();
            } catch (IllegalStateException e) {
                Harness.log("! the GPU timer is in use: " + e.getMessage());
            }
            for (int i = 0; i < repeat; i++) {
                try (RenderPass pass = encoder.createRenderPass(() -> "Albedo sky compare", target.getColorTextureView(), OptionalInt.empty())) {
                    pass.setPipeline(EQUIRECT.pipeline);
                    pass.setUniform("AlbedoSky", uniforms);
                    skyTerms.bind(pass);
                    pass.setVertexBuffer(0, quad);
                    pass.draw(0, 6);
                }
            }
            if (timer != null) encoder.timerQueryEnd(timer);
        } catch (RuntimeException e) {
            if (timer != null) timer.close();
            target.destroyBuffers();
            uniforms.close();
            skyTerms.close();
            quad.close();
            throw e;
        }
        GpuQuery query = timer;
        Screenshot.takeScreenshot(target, image -> {
            int[] argb = new int[width * height];
            NativeImagePixels pixels = new NativeImagePixels(image);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) argb[y * width + x] = pixels.getArgb(x, y);
            }
            if (png != null) {
                try {
                    image.writeToFile(png);
                } catch (IOException e) {
                    Harness.log("! cannot save " + png + ": " + e);
                }
            }
            image.close();
            OptionalLong t = query == null ? OptionalLong.empty() : query.getValue();
            long nanos = t.isPresent() ? t.getAsLong() / repeat : -1;
            if (query != null) query.close();
            target.destroyBuffers();
            uniforms.close();
            skyTerms.close();
            quad.close();
            done.accept(argb, nanos);
        });
        return true;
    }

    // Two triangles over the whole image, with texture coordinates from the top left corner.
    private static GpuBuffer quad() {
        float[][] corners = {{0, 0}, {0, 1}, {1, 1}, {0, 0}, {1, 1}, {1, 0}};
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(corners.length * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            for (float[] c : corners) b.addVertex(0, 0, 0).setUv(c[0], c[1]);
            try (MeshData mesh = b.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "Albedo sky compare quad", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
    }
}
*///?}
