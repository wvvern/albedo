package dev.albedo.modern;

//? if >=1.21.6 && <26.2 {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.albedo.core.Albedo;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
//? if >=1.21.11 {
import com.mojang.blaze3d.systems.GpuQuery;
import net.minecraft.resources.Identifier;
//?} else {
/^import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
^///?}

import java.nio.ByteBuffer;
import java.util.OptionalLong;

// The calls of the GPU device that changed from 1.21.6 to 1.21.11, for the sky of these versions.
final class DeviceApi {
    private DeviceApi() {}

    //? if >=1.21.11 {
    // The id of a resource of the mod.
    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Albedo.MOD_ID, path);
    }
    //?} else {
    /^static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Albedo.MOD_ID, path);
    }
    ^///?}

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
    static GpuBufferSlice transforms(Matrix4fc modelView, Vector4f color) {
        //? if >=1.21.11 {
        return RenderSystem.getDynamicUniforms().writeTransform(modelView, color, new Vector3f(), new Matrix4f());
        //?} else {
        /^return RenderSystem.getDynamicUniforms().writeTransform(modelView, color, new Vector3f(), new Matrix4f(), 0f);
        ^///?}
    }

    // Writes RGBA bytes into a mip level of a texture.
    static void write(CommandEncoder encoder, GpuTexture texture, ByteBuffer rgba, int level, int width, int height) {
        //? if >=1.21.10 {
        encoder.writeToTexture(texture, rgba, NativeImage.Format.RGBA, level, 0, 0, 0, width, height);
        //?} else {
        /^encoder.writeToTexture(texture, rgba.asIntBuffer(), NativeImage.Format.RGBA, level, 0, 0, 0, width, height);
        ^///?}
    }

    // A GPU time query, of the device from 1.21.11 and of the GL before it.
    static final class Timer {
        //? if >=1.21.11 {
        private final GpuQuery query;

        private Timer() {
            query = RenderSystem.getDevice().createCommandEncoder().timerQueryBegin();
        }

        // Ends the time of the work since begin.
        void end() {
            RenderSystem.getDevice().createCommandEncoder().timerQueryEnd(query);
        }

        // The GPU time in nanoseconds, or empty while the GPU does not have it.
        OptionalLong value() {
            return query.getValue();
        }

        // Frees the query.
        void close() {
            query.close();
        }
        //?} else {
        /^private final int query = GL15.glGenQueries();

        private Timer() {
            GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
        }

        void end() {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        }

        OptionalLong value() {
            if (GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) return OptionalLong.empty();
            return OptionalLong.of(GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT));
        }

        void close() {
            GL15.glDeleteQueries(query);
        }
        ^///?}

        // Starts a time, and throws IllegalStateException when the game allows no second time query now.
        static Timer begin() {
            return new Timer();
        }
    }
}
*///?}
