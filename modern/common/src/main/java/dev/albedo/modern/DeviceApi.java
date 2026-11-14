package dev.albedo.modern;

//? if device_sky || renderpearl {
//? if device_sky {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.albedo.core.sky.SkyShaderSource;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.OptionalDouble;
//? if >=26.2 {
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.systems.GpuQueryPool;
import net.minecraft.client.renderer.BindGroupLayouts;
import java.util.Optional;
//?} else {
/^import com.mojang.blaze3d.textures.TextureFormat;
import java.util.OptionalInt;
^///?}
//? if >=26.1 {
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
//?} else {
/^import com.mojang.blaze3d.platform.DepthTestFunction;
^///?}
//? if >=1.21.11 {
import com.mojang.blaze3d.textures.GpuSampler;
//?}
//? if >=1.21.11 && <26.2 {
import com.mojang.blaze3d.systems.GpuQuery;
//?}
*///?} else {
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;
//?}
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;
import java.util.OptionalLong;

// The calls of the GPU device that changed from 1.21.6 to 26.3, for the sky of 1.21.6 to 26.2 and the Chroma pass from 1.21.11.
final class DeviceApi {
    private DeviceApi() {}

    //? if device_sky {
    /*// The blend and the depth test of a pipeline that never writes depth, which the builder takes in other calls from 26.1, and
    // with a reversed depth buffer from 26.2, where the test passes the far depth 0 of the sky.
    static RenderPipeline.Builder state(RenderPipeline.Builder builder, boolean additive, boolean depthTest) {
        //? if >=26.2 {
        return builder.withColorTargetState(additive ? new ColorTargetState(BlendFunction.ADDITIVE) : ColorTargetState.DEFAULT)
                .withDepthStencilState(new DepthStencilState(depthTest ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.ALWAYS_PASS, false));
        //?} elif >=26.1 {
        /^return builder.withColorTargetState(additive ? new ColorTargetState(BlendFunction.ADDITIVE) : ColorTargetState.DEFAULT)
                .withDepthStencilState(new DepthStencilState(depthTest ? CompareOp.LESS_THAN_OR_EQUAL : CompareOp.ALWAYS_PASS, false));
        ^///?} else {
        /^if (additive) builder.withBlend(BlendFunction.ADDITIVE);
        else builder.withoutBlend();
        return builder.withDepthWrite(false)
                .withDepthTestFunction(depthTest ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST);
        ^///?}
    }

    // The pipeline state for a pass with no depth view: from 26.2 it has no depth state, because Vulkan builds the variant for such a pass only then.
    static RenderPipeline.Builder colorOnly(RenderPipeline.Builder builder) {
        //? if >=26.2 {
        return builder.withColorTargetState(ColorTargetState.DEFAULT);
        //?} else {
        /^return state(builder, false, false);
        ^///?}
    }

    // The blocks of the transforms of the game that the sky draws read, the globals, the projection and the transform of the draw.
    static RenderPipeline.Builder transformBlocks(RenderPipeline.Builder builder) {
        //? if >=26.2 {
        return builder.withBindGroupLayout(BindGroupLayouts.GLOBALS).withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS);
        //?} else {
        /^return builder.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).withUniform("Projection", UniformType.UNIFORM_BUFFER);
        ^///?}
    }

    static RenderPipeline.Builder uniformBlock(RenderPipeline.Builder builder, String name) {
        //? if >=26.2 {
        return builder.withBindGroupLayout(BindGroupLayout.builder().withUniform(name, UniformType.UNIFORM_BUFFER).build());
        //?} else {
        /^return builder.withUniform(name, UniformType.UNIFORM_BUFFER);
        ^///?}
    }

    static RenderPipeline.Builder sampler(RenderPipeline.Builder builder, String name) {
        //? if >=26.2 {
        return builder.withBindGroupLayout(BindGroupLayout.builder().withSampler(name).build());
        //?} else {
        /^return builder.withSampler(name);
        ^///?}
    }

    // Triangles with this vertex format, or with no vertex buffer when the format is null.
    static RenderPipeline.Builder triangles(RenderPipeline.Builder builder, VertexFormat format) {
        //? if >=26.2 {
        builder.withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
        return format == null ? builder : builder.withVertexBinding(0, format);
        //?} else {
        /^return builder.withVertexFormat(format == null ? DefaultVertexFormat.EMPTY : format, VertexFormat.Mode.TRIANGLES);
        ^///?}
    }

    // A vertex buffer of triangles from the numbers x, y, z, u, v of each vertex.
    static GpuBuffer vertexBuffer(String label, float[] v) {
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(v.length / 5 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            //? if >=26.2 {
            BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            //?} else {
            /^BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            ^///?}
            for (int i = 0; i < v.length; i += 5) b.addVertex(v[i], v[i + 1], v[i + 2]).setUv(v[i + 3], v[i + 4]);
            try (MeshData mesh = b.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
    }

    // True when the device is the OpenGL one, which the raw GL calls of the sharp sky need; before 26.2 it always is.
    static boolean isGl() {
        //? if >=26.2 {
        return "OpenGL".equals(RenderSystem.getDevice().getDeviceInfo().backendName());
        //?} else {
        /^return true;
        ^///?}
    }

    static RenderTarget mainTarget() {
        //? if >=26.2 {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
        //?} else {
        /^return Minecraft.getInstance().getMainRenderTarget();
        ^///?}
    }

    // The model view matrix of the sky pass of the game.
    static Matrix4fc modelView() {
        //? if >=26.2 {
        return RenderSystem.getModelViewMatrixCopy();
        //?} else {
        /^return RenderSystem.getModelViewMatrix();
        ^///?}
    }

    // The dialect of the shader text of the mod: the depth buffer is reversed from 26.2, and the clip volume can start at 0.
    static SkyShaderSource.Dialect dialect() {
        //? if >=26.2 {
        return RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()
                ? SkyShaderSource.Dialect.BLOCKS_330_REVERSED_ZERO_TO_ONE : SkyShaderSource.Dialect.BLOCKS_330_REVERSED;
        //?} else {
        /^return SkyShaderSource.Dialect.BLOCKS_330;
        ^///?}
    }

    // A render pass that keeps the colors and the depth of the views.
    static RenderPass pass(String label, GpuTextureView color, GpuTextureView depth) {
        //? if >=26.2 {
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, color, Optional.empty(), depth,
                OptionalDouble.empty());
        //?} else {
        /^return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, color, OptionalInt.empty(), depth,
                OptionalDouble.empty());
        ^///?}
    }

    // Draws vertices from the vertex buffer 0 with the pipeline of the pass.
    static void draw(RenderPass pass, GpuBuffer vertices, int count) {
        //? if >=26.2 {
        pass.setVertexBuffer(0, vertices.slice());
        pass.draw(count, 1, 0, 0);
        //?} else {
        /^pass.setVertexBuffer(0, vertices);
        pass.draw(0, count);
        ^///?}
    }

    // A render target of 8 bits for each channel with no depth.
    static TextureTarget target(String label, int width, int height) {
        //? if >=26.2 {
        return new TextureTarget(label, width, height, false, GpuFormat.RGBA8_UNORM);
        //?} else {
        /^return new TextureTarget(label, width, height, false);
        ^///?}
    }

    // Binds a texture to a sampler of the pass with linear filters, with a repeat around the sky and a clamp at its poles.
    static void bindSky(RenderPass pass, String sampler, GpuTextureView view) {
        //? if >=1.21.11 {
        pass.bindTexture(sampler, view, RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, false));
        //?} else {
        /^view.texture().setAddressMode(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE);
        view.texture().setTextureFilter(FilterMode.LINEAR, false);
        pass.bindSampler(sampler, view);
        ^///?}
    }

    // The transforms of a draw with this model view matrix and color modulator.
    static GpuBufferSlice transforms(Matrix4f modelView, Vector4f color) {
        //? if >=1.21.11 {
        return RenderSystem.getDynamicUniforms().writeTransform(modelView, color, new Vector3f(), new Matrix4f());
        //?} else {
        /^return RenderSystem.getDynamicUniforms().writeTransform(modelView, color, new Vector3f(), new Matrix4f(), 0f);
        ^///?}
    }
    *///?}

    // A render pass that keeps the colors of the view, with no depth.
    static RenderPass pass(String label, GpuTextureView color) {
        //? if >=26.2 {
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, color, Optional.empty());
        //?} else {
        /*return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, color, OptionalInt.empty());
        *///?}
    }

    // Draws vertices that the vertex shader makes from their number alone.
    static void draw(RenderPass pass, int count) {
        //? if >=26.2 {
        pass.draw(count, 1, 0, 0);
        //?} else {
        /*pass.draw(0, count);
        *///?}
    }

    // A texture with 8 bits for each of the four channels.
    static GpuTexture texture(String label, int usage, int width, int height, int levels) {
        //? if >=26.2 {
        return RenderSystem.getDevice().createTexture(() -> label, usage, GpuFormat.RGBA8_UNORM, width, height, 1, levels);
        //?} else {
        /*return RenderSystem.getDevice().createTexture(() -> label, usage, TextureFormat.RGBA8, width, height, 1, levels);
        *///?}
    }

    // Writes RGBA bytes into a mip level of a texture.
    static void write(CommandEncoder encoder, GpuTexture texture, ByteBuffer rgba, int level, int width, int height) {
        //? if >=26.2 {
        encoder.writeToTexture(texture, rgba, level, 0, 0, 0, width, height);
        //?} elif >=1.21.10 {
        /*encoder.writeToTexture(texture, rgba, NativeImage.Format.RGBA, level, 0, 0, 0, width, height);
        *///?} else {
        /*encoder.writeToTexture(texture, rgba.asIntBuffer(), NativeImage.Format.RGBA, level, 0, 0, 0, width, height);
        *///?}
    }

    //? if device_chroma {
    // The sampler with nearest filters and a clamp at the edges.
    static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    // Binds a texture view with a sampler to a sampler of the pass.
    static void bindTexture(RenderPass pass, String name, GpuTextureView view, GpuSampler sampler) {
        //? if renderpearl {
        pass.setUniform(name, view, sampler);
        //?} else {
        /*pass.bindTexture(name, view, sampler);
        *///?}
    }
    //?}

    // A GPU time query, of the timestamps of the device from 26.2, of its time query from 1.21.11 and of the GL before it.
    static final class Timer {
        //? if >=26.2 {
        private final GpuQueryPool pool = RenderSystem.getDevice().createTimestampQueryPool(2);

        private Timer() {
            RenderSystem.getDevice().createCommandEncoder().writeTimestamp(pool, 0);
        }

        void end() {
            RenderSystem.getDevice().createCommandEncoder().writeTimestamp(pool, 1);
        }

        // The GPU time in nanoseconds, or empty while the GPU does not have it.
        OptionalLong value() {
            OptionalLong[] t = pool.getValues(0, 2);
            if (!t[0].isPresent() || !t[1].isPresent()) return OptionalLong.empty();
            return OptionalLong.of((long) ((t[1].getAsLong() - t[0].getAsLong()) * RenderSystem.getDevice().getDeviceInfo().timestampPeriod()));
        }

        void close() {
            pool.close();
        }
        //?} elif >=1.21.11 {
        /*private final GpuQuery query;

        private Timer() {
            query = RenderSystem.getDevice().createCommandEncoder().timerQueryBegin();
        }

        void end() {
            RenderSystem.getDevice().createCommandEncoder().timerQueryEnd(query);
        }

        OptionalLong value() {
            return query.getValue();
        }

        void close() {
            query.close();
        }
        *///?} else {
        /*private final GlTimeQuery query = new GlTimeQuery();

        private Timer() {
            query.begin();
        }

        void end() {
            query.end();
        }

        OptionalLong value() {
            return query.value();
        }

        void close() {
            query.close();
        }
        *///?}

        // Starts a time, and throws IllegalStateException when the game allows no second time query now.
        static Timer begin() {
            return new Timer();
        }
    }
}
//?}
