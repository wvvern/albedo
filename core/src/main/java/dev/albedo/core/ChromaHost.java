package dev.albedo.core;

import dev.albedo.core.texture.ChromaFrames;

/** The core side of the GPU pass of Chroma, which reads the instance of {@link Albedo} at each call. */
public final class ChromaHost implements ChromaFrames.Host {
    public static final ChromaHost INSTANCE = new ChromaHost();

    private ChromaHost() {}

    @Override
    public boolean running() {
        Albedo albedo = Albedo.get();
        return albedo != null && albedo.chromaGpuRunning();
    }

    @Override
    public void stopGpu() {
        Albedo albedo = Albedo.get();
        if (albedo != null) albedo.chromaGpu(false);
    }

    @Override
    public void frameDrawn(double seconds) {
        Albedo albedo = Albedo.get();
        if (albedo != null) albedo.frameDrawn(seconds);
    }

    @Override
    public int texturePaints() {
        return Albedo.texturePaints();
    }

    @Override
    public boolean texels(String location, int w, int h, int[] out) {
        return Albedo.chromaTexels(location, w, h, out);
    }

    @Override
    public void info(String message) {
        Albedo albedo = Albedo.get();
        if (albedo != null) albedo.platform().info(message);
    }

    @Override
    public void warn(String message, Throwable error) {
        Albedo albedo = Albedo.get();
        if (albedo != null) albedo.platform().warn(message, error);
    }
}
