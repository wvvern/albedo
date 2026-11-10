package dev.albedo.modern;

//? if gl_core || frame_graph {
/*import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

// Triangles with a position and a texture coordinate for each vertex, in a vertex array and a buffer that the mod owns.
final class GlMesh {
    private static final int FLOATS = 5;

    private final int vertexArray = GlStateManager._glGenVertexArrays();
    private final int buffer = GlStateManager._glGenBuffers();
    private final int count;

    // Sends vertices of five floats each (x, y, z, u, v) to the GPU.
    GlMesh(float[] vertices) {
        count = vertices.length / FLOATS;
        GlState saved = GlState.ofAll();
        GlStateManager._glBindVertexArray(vertexArray);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, FLOATS * 4, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, FLOATS * 4, 12L);
        saved.restore();
    }

    // Draws the triangles with the program in use, and leaves the vertex array of the mesh bound for the GlState of the caller to put back.
    void draw() {
        GlStateManager._glBindVertexArray(vertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
    }

    void close() {
        GlStateManager._glDeleteVertexArrays(vertexArray);
        GlStateManager._glDeleteBuffers(buffer);
    }
}
*///?}
