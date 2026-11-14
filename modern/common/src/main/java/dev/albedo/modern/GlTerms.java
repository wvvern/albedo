package dev.albedo.modern;

//? if <26.2 {
/*import dev.albedo.core.sky.SkyImage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

// The term texture of a sky through the raw GL, for the versions whose game has no half float texture format.
final class GlTerms {
    // The bytes of one RGBA texel of half floats.
    private static final int HALF_RGBA_BYTES = 8;

    private GlTerms() {}

    // Sends the term images of a sky, one under the other, as half floats to the bound texture of the active unit.
    static void texImage(SkyImage img) {
        int width = img.termTextureWidth(), height = img.termTextureHeight();
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        ByteBuffer bytes = MemoryUtil.memCalloc(width * height * HALF_RGBA_BYTES);
        try {
            bytes.asShortBuffer().put(img.terms());
            // GL30 has the same numbers as ARB_texture_float and ARB_half_float_pixel.
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL30.GL_HALF_FLOAT, bytes);
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }
}
*///?}
