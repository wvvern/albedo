package dev.albedo.legacy;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** A color texture and a framebuffer of the harness, the twin of the target in FixedSharpSky.drawEquirect. */
final class LegacyOffscreen {
    private final int width;
    private final int height;
    private final int framebufferBefore;
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final int color;
    private final int target;

    private LegacyOffscreen(int width, int height) {
        this.width = width;
        this.height = height;
        framebufferBefore = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        // A framebuffer of its own, because a Framebuffer of the game binds nothing while OptiFine Fast Render is on.
        color = GlStateManager.generateTexture();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(color);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        target = OpenGlHelper.glGenFramebuffers();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
        GL11.glViewport(0, 0, width, height);
    }

    /** Saves the bound framebuffer and the viewport, then binds a new target of this size with the full viewport. */
    static LegacyOffscreen open(int width, int height) {
        return new LegacyOffscreen(width, height);
    }

    /** The pixels of the target as GL_BGRA and GL_UNSIGNED_INT_8_8_8_8_REV, from the bottom row up. */
    IntBuffer readBgra() {
        IntBuffer pixels = BufferUtils.createIntBuffer(width * height);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        return pixels;
    }

    /** Binds the saved framebuffer and viewport again and deletes the target. */
    void close() {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebufferBefore);
        GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        OpenGlHelper.glDeleteFramebuffers(target);
        GlStateManager.deleteTexture(color);
    }
}
