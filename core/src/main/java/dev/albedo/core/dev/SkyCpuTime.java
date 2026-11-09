package dev.albedo.core.dev;

/** The CPU time of the custom sky on the render thread, which the adapters add and the harness command {@code fps} logs. */
public final class SkyCpuTime {
    private static long nanos;

    private SkyCpuTime() {}

    /** The start of a part of the sky path, for {@link #add}. */
    public static long start() {
        return System.nanoTime();
    }

    /** Adds the time from a {@link #start} to now. */
    public static void add(long start) {
        nanos += System.nanoTime() - start;
    }

    /** The time in nanoseconds since the last call, which starts again from 0. */
    static long take() {
        long n = nanos;
        nanos = 0;
        return n;
    }
}
