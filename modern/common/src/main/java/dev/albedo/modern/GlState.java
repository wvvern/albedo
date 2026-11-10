package dev.albedo.modern;

//? if <1.21.11 {
/*import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

// The GL state that the raw GL draws of the mod change, read from the GL before a draw and put back after it, through
// GlStateManager where the game keeps a copy of the state.
final class GlState {
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int[] viewport = new int[4];
    private final int activeTexture = GlStateManager._getActiveTexture();
    private final int texture0;
    private final int texture1;
    private final boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final int blendSourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int blendDestRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int blendSourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int blendDestAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

    // Reads the state, and leaves it as it is.
    GlState() {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(activeTexture);
    }

    // Puts back the state that the constructor read.
    void restore() {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(texture1);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(texture0);
        GlStateManager._activeTexture(activeTexture);
        GlStateManager._glUseProgram(program);
        GlStateManager._glBindVertexArray(vertexArray);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        if (depthTest) GlStateManager._enableDepthTest();
        else GlStateManager._disableDepthTest();
        GlStateManager._depthFunc(depthFunc);
        GlStateManager._depthMask(depthMask);
        if (blend) GlStateManager._enableBlend();
        else GlStateManager._disableBlend();
        GlStateManager._blendFuncSeparate(blendSourceRgb, blendDestRgb, blendSourceAlpha, blendDestAlpha);
        if (cull) GlStateManager._enableCull();
        else GlStateManager._disableCull();
        if (scissor) GlStateManager._enableScissorTest();
        else GlStateManager._disableScissorTest();
    }
}
*///?}
