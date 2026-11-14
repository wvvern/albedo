package dev.albedo.legacy;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

/** One GL_TIME_ELAPSED query for the harness, which measures nothing and gives 0 when the GPU has no timer query. */
final class LegacyGpuTimer {
    private final int query;

    LegacyGpuTimer() {
        query = supported() ? GL15.glGenQueries() : 0;
    }

    /** Whether the GPU has the timer query of OpenGL 3.3 or ARB_timer_query. */
    static boolean supported() {
        ContextCapabilities caps = GLContext.getCapabilities();
        return caps.OpenGL33 || caps.GL_ARB_timer_query;
    }

    void begin() {
        if (query != 0) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
    }

    void end() {
        if (query != 0) GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
    }

    /** Whether the GPU gave the time, so that {@link #nanos} does not wait. */
    boolean available() {
        return query == 0 || GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT_AVAILABLE) != GL11.GL_FALSE;
    }

    /** The GPU time between begin and end in nanoseconds, which waits for the GPU. */
    long nanos() {
        return query == 0 ? 0 : GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT);
    }

    void close() {
        if (query != 0) GL15.glDeleteQueries(query);
    }
}
