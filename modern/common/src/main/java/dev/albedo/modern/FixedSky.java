package dev.albedo.modern;

//? if gl_fixed {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Matrix4f;
import dev.albedo.core.Albedo;
import dev.albedo.core.sky.SkyDefer;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyTurn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.FloatBuffer;

// The custom sky of the fixed function versions: the sky shader of FixedSharpSky when it can draw, else the baked image on the
// sphere, in place of the vanilla sky disc, with a display list of the sphere and a texture that the mod owns.
// Twin of legacy LegacySky: a fix lands in both, with the steps in the same order.
public final class FixedSky {
    // The sky of the sky pass that waits for the solid world: the camera matrix of the sky disc and the turn of the sky.
    private static final class Waiting {
        final Matrix4f camera;
        final SkyTurn turn;
        final float brightness;

        Waiting(Matrix4f camera, SkyTurn turn, float brightness) {
            this.camera = camera;
            this.turn = turn;
            this.brightness = brightness;
        }
    }

    private static DynamicTexture texture;
    private static long uploaded = -1;
    private static int sphere;
    // The matrix of a turning sky for the GL, which each draw fills again.
    private static final FloatBuffer TURN = BufferUtils.createFloatBuffer(16);
    private static final SkyDefer<Waiting> DEFER = new SkyDefer<Waiting>();
    // True when the last sky disc of a sky pass was the custom sky, which the later parts of the same pass read.
    private static boolean replacedDisc;

    private FixedSky() {}

    static void frameStart() {
        DEFER.frameStart();
    }

    // Draws the sky in place of the sky disc with the camera matrix of the disc, and returns false when the game must draw its disc.
    public static boolean draw(Matrix4f camera) {
        replacedDisc = drawSphere(camera);
        return replacedDisc;
    }

    // True when the custom sky drew in place of the sky disc of this sky pass, so the dark lower disc and the sunrise fan must not draw.
    public static boolean replacedDisc() {
        return replacedDisc;
    }

    // True when the custom sky drew in this sky pass and hides the vanilla stars.
    public static boolean hidesStars() {
        return replacedDisc && ModernSky.hidesStars();
    }

    // Draws the sky sphere, but not in the fog of a fluid or of blindness, which the fogged vanilla sky shows.
    private static boolean drawSphere(Matrix4f camera) {
        SkyImage img = Albedo.skyImage();
        if (img == null || ModernHooks.fogHidesSky(Minecraft.getInstance().gameRenderer.getMainCamera())) return false;
        prepare(img);
        float k = ModernSky.brightness(img);
        SkyTurn turn = ModernSky.turn(img);
        if (FixedSharpSky.prepare(img, turn)) {
            // The sharp sky adds its color to the frame, so the sky of this level view must be black below it.
            drawBlack(camera, turn);
            // The sharp sky waits for the solid world, which then hides a part of it from the shader.
            if (DEFER.defer(new Waiting(new Matrix4f(camera), turn, k))) return true;
            drawSharp(camera, turn, k);
            return true;
        }
        RenderSystem.pushMatrix();
        RenderSystem.multMatrix(camera);
        turnMatrix(turn);
        RenderSystem.enableTexture();
        RenderSystem.disableFog();
        RenderSystem.disableCull();
        RenderSystem.color4f(k, k, k, 1f);
        GlStateManager._bindTexture(texture.getId());
        GL11.glCallList(sphere);
        RenderSystem.enableCull();
        RenderSystem.enableFog();
        RenderSystem.disableTexture();
        RenderSystem.color4f(1f, 1f, 1f, 1f);
        RenderSystem.popMatrix();
        return true;
    }

    // Multiplies the model view matrix of the GL by the matrix of a sky that turns.
    private static void turnMatrix(SkyTurn turn) {
        if (turn.still()) return;
        TURN.clear();
        TURN.put(turn.matrix()).flip();
        GL11.glMultMatrixf(TURN);
    }

    // Draws black on the sphere at the far depth with no blend, texture or fog, so that only the pixels that still have the
    // far depth of the clear become black, and leaves the depth test on as the sharp sky does.
    private static void drawBlack(Matrix4f camera, SkyTurn turn) {
        RenderSystem.pushMatrix();
        RenderSystem.multMatrix(camera);
        turnMatrix(turn);
        RenderSystem.disableTexture();
        RenderSystem.disableFog();
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.color4f(0f, 0f, 0f, 1f);
        // The game never changes the depth range, so the sphere gets the far depth here and the range goes back to the full range after it.
        GL11.glDepthRange(1.0, 1.0);
        GL11.glCallList(sphere);
        GL11.glDepthRange(0.0, 1.0);
        RenderSystem.color4f(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        RenderSystem.enableFog();
        RenderSystem.popMatrix();
    }

    private static void drawSharp(Matrix4f camera, SkyTurn turn, float brightness) {
        RenderSystem.pushMatrix();
        RenderSystem.multMatrix(camera);
        turnMatrix(turn);
        FixedSharpSky.draw(sphere, brightness);
        RenderSystem.popMatrix();
    }

    // Draws the sharp sky that waits, after the solid world, only on the pixels that the world left empty.
    public static void afterSolid() {
        Waiting waiting = DEFER.take();
        if (waiting == null) return;
        drawSharp(waiting.camera, waiting.turn, waiting.brightness);
    }

    // Sends a new image to the texture of the baked image, and makes the sphere once; the render thread calls this.
    private static void prepare(SkyImage img) {
        ensureTexture(img);
        ensureSphere();
    }

    private static void ensureTexture(SkyImage img) {
        if (texture == null || texture.getPixels().getWidth() != img.width() || texture.getPixels().getHeight() != img.height()) {
            if (texture != null) texture.close();
            texture = new DynamicTexture(img.width(), img.height(), false);
            uploaded = -1;
        }
        if (uploaded != img.serial()) {
            NativeImagePixels out = new NativeImagePixels(texture.getPixels());
            int[] argb = img.argb();
            for (int y = 0; y < img.height(); y++) {
                for (int x = 0; x < img.width(); x++) out.setArgb(x, y, argb[y * img.width() + x]);
            }
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            texture.upload();
            // The upload sets nearest filtering and repeat in both directions.
            GlStateManager._bindTexture(texture.getId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._bindTexture(previous);
            GlStateManager._activeTexture(active);
            uploaded = img.serial();
        }
    }

    private static void ensureSphere() {
        if (sphere != 0) return;
        sphere = GL11.glGenLists(1);
        GL11.glNewList(sphere, GL11.GL_COMPILE);
        FixedSharpSky.drawTriangles(SkyImage.sphere(ModernSky.RADIUS));
        GL11.glEndList();
    }
}
*///?}
