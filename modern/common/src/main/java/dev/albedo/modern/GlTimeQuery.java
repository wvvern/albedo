package dev.albedo.modern;

//? if <1.21.11 {
/*import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;

import java.util.OptionalLong;

// One GL_TIME_ELAPSED query of the raw GL, for the Chroma timer and the GPU time of the harness.
// LegacyGpuTimer is its twin on 1.8.9.
final class GlTimeQuery implements ChromaBackend.Timer {
    private final int query = GL15.glGenQueries();

    // True when the GPU has the timer query of OpenGL 3.3 or ARB_timer_query.
    static boolean supported() {
        GLCapabilities caps = GL.getCapabilities();
        return caps.OpenGL33 || caps.GL_ARB_timer_query;
    }

    // Runs a draw repeat times and returns the GPU time of one draw in nanoseconds, or -1 when the GPU has no timer query.
    static long timeEach(int repeat, Runnable draw) {
        if (!supported()) {
            for (int i = 0; i < repeat; i++) draw.run();
            return -1;
        }
        GlTimeQuery query = new GlTimeQuery();
        query.begin();
        for (int i = 0; i < repeat; i++) draw.run();
        query.end();
        long nanos = query.waitNanos() / repeat;
        query.close();
        return nanos;
    }

    @Override
    public void begin() {
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
    }

    @Override
    public void end() {
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
    }

    // The GPU time between begin and end in nanoseconds, or empty while the GPU does not have it.
    OptionalLong value() {
        if (GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) return OptionalLong.empty();
        return OptionalLong.of(waitNanos());
    }

    @Override
    public long nanos() {
        return value().orElse(-1);
    }

    // The GPU time between begin and end in nanoseconds, which waits for the GPU.
    long waitNanos() {
        return GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT);
    }

    @Override
    public void close() {
        GL15.glDeleteQueries(query);
    }
}
*///?}
