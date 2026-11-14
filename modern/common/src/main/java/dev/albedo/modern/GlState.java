package dev.albedo.modern;

//? if gl_program {
/*import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
//? if >=1.21.5 {
import com.mojang.blaze3d.opengl.GlStateManager;
//?} else {
/^import com.mojang.blaze3d.platform.GlStateManager;
^///?}

// The GL state that the raw GL work of the mod changes, read from the GL before the work and put back after it, through
// GlStateManager where the game keeps a copy of the state.
final class GlState {
    private final boolean targets;
    private final boolean vertexArrays;
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private int vertexArray;
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int texture0;
    private final boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final int blendSourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int blendDestRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int blendSourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int blendDestAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private int arrayBuffer;
    private int drawFramebuffer;
    private int readFramebuffer;
    private final int[] viewport = new int[4];
    private int texture1;
    private boolean scissor;
    private int packBuffer;
    private int unpackBuffer;

    private GlState(boolean targets, boolean vertexArrays) {
        this.targets = targets;
        this.vertexArrays = vertexArrays;
        if (vertexArrays) vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (targets) {
            // The pixel buffers stay unbound until restore, so that the pixel transfers of the work use client memory.
            packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        }
        GlStateManager._activeTexture(activeTexture);
    }

    // Reads the state of a draw into the frame: the program, the vertex array, the texture of unit 0, the depth state, the blend state and the cull.
    static GlState ofDraw() {
        return new GlState(false, true);
    }

    // Reads the state of a draw with no vertex array, which OpenGL 2.0 of the fixed function versions may not have.
    static GlState ofFixedDraw() {
        return new GlState(false, false);
    }

    // Reads the state of a draw and also the array buffer, the framebuffers, the viewport, the texture of unit 1, the scissor test and the pixel buffers.
    static GlState ofAll() {
        return new GlState(true, true);
    }

    // Puts back the state that this object read.
    void restore() {
        if (targets) {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GlStateManager._bindTexture(texture1);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
            if (scissor) GlStateManager._enableScissorTest();
            else GlStateManager._disableScissorTest();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(texture0);
        GlStateManager._activeTexture(activeTexture);
        GlStateManager._glUseProgram(program);
        //? if gl_fixed {
        /^if (vertexArrays) GL30.glBindVertexArray(vertexArray);
        ^///?} else {
        if (vertexArrays) GlStateManager._glBindVertexArray(vertexArray);
        //?}
        if (depthTest) GlStateManager._enableDepthTest();
        else GlStateManager._disableDepthTest();
        GlStateManager._depthFunc(depthFunc);
        GlStateManager._depthMask(depthMask);
        if (blend) GlStateManager._enableBlend();
        else GlStateManager._disableBlend();
        GlStateManager._blendFuncSeparate(blendSourceRgb, blendDestRgb, blendSourceAlpha, blendDestAlpha);
        if (cull) GlStateManager._enableCull();
        else GlStateManager._disableCull();
    }
}
*///?}
