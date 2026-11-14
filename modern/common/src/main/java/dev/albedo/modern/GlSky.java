package dev.albedo.modern;

//? if gl_sky {
/*import com.mojang.blaze3d.systems.RenderSystem;
import dev.albedo.core.Albedo;
import dev.albedo.core.Colors;
import dev.albedo.core.sky.SkyDefer;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyMesh;
import dev.albedo.core.sky.SkyShaderSource;
import dev.albedo.core.sky.SkyTurn;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;
//? if >=1.21.5 {
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
//?} else {
/^import com.mojang.blaze3d.platform.GlStateManager;
^///?}

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

// The custom sky of the versions that draw through the raw GL: the sky shader of GlSharpSky when it can draw, else the
// baked image on the sphere, with a vertex array, a texture and a GL program that the mod owns.
public final class GlSky {
    // The baked image on the sky sphere, times a brightness.
    private static final class Baked extends GlProgram {
        int modelView;
        int projection;
        int color;
        int sampler;

        Baked() {
            super(SkyShaderSource.BAKED, SkyShaderSource.BAKED, "", SkyShaderSource.Dialect.UNIFORMS_150, "sky texture shader",
                    "The game draws its own sky.");
        }

        @Override
        void linked() {
            modelView = uniform("ModelViewMat");
            projection = uniform("ProjMat");
            color = uniform("ColorModulator");
            sampler = uniform("Sampler0");
        }
    }

    private static final Baked BAKED = new Baked();
    private static int texture;
    private static long uploaded = -1;
    private static GlMesh sphere;
    // The sharp sky of the sky pass that waits for the solid world.
    private static final class Waiting {
        final float[] modelView;
        final float[] projection;
        final float brightness;

        Waiting(float[] modelView, float[] projection, float brightness) {
            this.modelView = modelView;
            this.projection = projection;
            this.brightness = brightness;
        }
    }

    private static final SkyDefer<Waiting> DEFER = new SkyDefer<>();

    private GlSky() {}

    static void frameStart() {
        DEFER.frameStart();
    }

    // Sends a new image to the texture and makes the sphere once; the render thread calls this.
    private static void prepare(SkyImage img) {
        if (uploaded != img.serial()) {
            GlState saved = GlState.ofAll();
            if (texture == 0) texture = GlStateManager._genTexture();
            upload(img);
            uploaded = img.serial();
            saved.restore();
        }
        if (sphere == null) sphere = new GlMesh(SkyMesh.sphere(ModernSky.RADIUS));
    }

    // Writes the baked image into the texture on unit 0, with a linear filter and a repeat only around the sky.
    private static void upload(SkyImage img) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        int[] argb = img.argb();
        ByteBuffer bytes = MemoryUtil.memAlloc(argb.length * 4);
        try {
            // An ABGR number in the byte order of the computer is the bytes red, green, blue and alpha.
            for (int i = 0; i < argb.length; i++) bytes.putInt(i * 4, Colors.swapRedBlue(argb[i]));
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, img.width(), img.height(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    // Draws the sky with the matrices of the vanilla disc, and returns false when the game must draw its disc.
    //? if >=1.19.3 {
    public static boolean draw(org.joml.Matrix4fc skyModelView, org.joml.Matrix4fc projection) {
        return drawColumns(skyModelView.get(new float[16]), projection.get(new float[16]));
    }
    //?} else {
    /^public static boolean draw(com.mojang.math.Matrix4f skyModelView, com.mojang.math.Matrix4f projection) {
        return drawColumns(columns(skyModelView), columns(projection));
    }

    // The 16 floats of a matrix in column order, which is how a GL uniform takes them.
    private static float[] columns(com.mojang.math.Matrix4f matrix) {
        java.nio.FloatBuffer buffer = java.nio.FloatBuffer.allocate(16);
        matrix.store(buffer);
        return buffer.array();
    }
    ^///?}

    private static boolean drawColumns(float[] skyModelView, float[] projection) {
        boolean drawn = drawSphere(skyModelView, projection);
        ModernSky.discDrawn(drawn);
        return drawn;
    }

    //? if >=1.21.2 {
    // Draws the sky in place of the sky disc of SkyRenderer with the matrices of RenderSystem, and returns false when the game must draw its disc.
    public static boolean drawDisc() {
        try (Bound ignored = mainTarget()) {
            return draw(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix());
        }
    }
    //?}

    // A main render target that the raw GL draws into until close.
    private interface Bound extends AutoCloseable {
        @Override
        void close();
    }

    // Binds the main render target: from 1.21.5 the game leaves no target bound between its render passes, so an empty
    // render pass of the device binds it, and before 1.21.5 the target of the sky stays bound.
    private static Bound mainTarget() {
        //? if >=1.21.5 {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                target.getColorTexture(), OptionalInt.empty(), target.getDepthTexture(), OptionalDouble.empty());
        return pass::close;
        //?} else {
        /^return () -> { };
        ^///?}
    }

    // Draws the sky sphere, but not with a shader pack, whose framebuffer needs the programs of the pack, and not where the game draws no sky (blindness on 1.17).
    private static boolean drawSphere(float[] skyModelView, float[] projection) {
        SkyImage img = Albedo.skyImage();
        if (img == null || ModernSky.vanillaWithPack() || ModernFog.skySkipped(ModernSky.mainCamera())) return false;
        prepare(img);
        float k = ModernSky.brightness(img);
        float[] modelView = turned(skyModelView, ModernSky.turn(img));
        if (GlSharpSky.prepare(img)) {
            GlSharpSky.drawBlack(sphere, modelView, projection);
            if (!DEFER.defer(new Waiting(modelView, projection, k))) GlSharpSky.draw(sphere, modelView, projection, k);
            return true;
        }
        if (!BAKED.ready()) return false;
        GlState saved = GlState.ofDraw();
        GlStateManager._glUseProgram(BAKED.id());
        GL20.glUniformMatrix4fv(BAKED.modelView, false, modelView);
        GL20.glUniformMatrix4fv(BAKED.projection, false, projection);
        GL20.glUniform4f(BAKED.color, k, k, k, 1f);
        GL20.glUniform1i(BAKED.sampler, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(texture);
        GlStateManager._disableCull();
        sphere.draw();
        saved.restore();
        return true;
    }

    // The cutout layer is the last solid layer of the world.
    public static void afterLayer(net.minecraft.client.renderer.RenderType type) {
        if (type == net.minecraft.client.renderer.RenderType.cutout()) afterSolid();
    }

    // Draws the sharp sky that waits, after the solid world, only on the pixels that the world left empty.
    private static void afterSolid() {
        ModernHooks.AFTER_SOLID.run();
        Waiting waiting = DEFER.take();
        if (waiting == null) return;
        try (Bound ignored = mainTarget()) {
            GlSharpSky.draw(sphere, waiting.modelView, waiting.projection, waiting.brightness);
        }
    }

    // A model view matrix in column order times the rotation of a sky that turns.
    private static float[] turned(float[] modelView, SkyTurn turn) {
        if (turn.still()) return modelView;
        float[] t = turn.matrix();
        float[] out = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                out[col * 4 + row] = modelView[row] * t[col * 4] + modelView[4 + row] * t[col * 4 + 1]
                        + modelView[8 + row] * t[col * 4 + 2] + modelView[12 + row] * t[col * 4 + 3];
            }
        }
        return out;
    }
}
*///?}
