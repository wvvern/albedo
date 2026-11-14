package dev.albedo.core.texture;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * The harness timing of the GPU pass of Chroma for the command chromatime, which each adapter calls around the recolor
 * at the start of a frame and the restore at its end, on the render thread.
 */
public final class ChromaTiming {
    /** The GPU time between {@link #begin} and {@link #end}, which the GPU gives some frames later. */
    public interface GpuTimer extends AutoCloseable {
        void begin();

        void end();

        /** The time in nanoseconds, or -1 while the GPU did not give it yet. */
        long nanos();

        @Override
        void close();
    }

    /** Takes the times of the measured frames; the adapters pass {@code Harness::logChromaTimes}. */
    public interface TimesSink {
        /**
         * @param frames for each frame in nanoseconds: the CPU time of the pass, the GPU time of the recolor and the GPU
         *     time of the restore
         * @param bytes the GPU memory of the pass
         * @param atlases the names of the atlases that the pass turns
         */
        void accept(List<long[]> frames, long bytes, String atlases);
    }

    /** The GPU work of a frame that the timing measures. */
    public enum Part { RECOLOR, RESTORE }

    private final Supplier<? extends GpuTimer> timers;
    private final TimesSink sink;
    /** The frames that the harness still measures. */
    private int timedLeft;
    /** The timers of the measured frames whose GPU times did not come yet, in the order of the frames. */
    private final List<GpuTimer[]> pending = new ArrayList<GpuTimer[]>();
    /** The times of the measured frames, where the last {@code pending.size()} rows have only the CPU time. */
    private final List<long[]> times = new ArrayList<long[]>();
    /** The timers of this frame, or null when this frame is not measured. */
    private GpuTimer[] frame;
    private long recolorCpu;

    public ChromaTiming(Supplier<? extends GpuTimer> timers, TimesSink sink) {
        this.timers = timers;
        this.sink = sink;
    }

    /** Measures the next frames. */
    public void start(int frames) {
        // A measurement that did not end yet gives way to the new one, and its timers close.
        for (GpuTimer[] t : pending) close(t);
        if (frame != null) close(frame);
        frame = null;
        times.clear();
        pending.clear();
        timedLeft = frames;
    }

    /** Makes the timers of this frame when the harness measures it, before the recolor. */
    public void frameBegin() {
        if (timedLeft > 0) frame = new GpuTimer[]{timers.get(), timers.get()};
    }

    public void begin(Part part) {
        if (frame != null) frame[part.ordinal()].begin();
    }

    public void end(Part part) {
        if (frame != null) frame[part.ordinal()].end();
    }

    /** The CPU time of the pass at the start of this frame. */
    public void recolorCpu(long nanos) {
        recolorCpu = nanos;
    }

    /** Ends a measured frame after the restore, which took {@code restoreCpu} nanoseconds on the CPU. */
    public void frameEnd(long restoreCpu) {
        if (frame == null) return;
        pending.add(frame);
        times.add(new long[]{recolorCpu + restoreCpu});
        frame = null;
        timedLeft--;
    }

    /** Whether a measured frame waits for its GPU times, so that the adapter calls {@link #collect}. */
    public boolean collecting() {
        return !pending.isEmpty();
    }

    /** Takes the GPU times that the GPU gave, and gives all times to the sink when the last measured frame has its times. */
    public void collect(long bytes, String atlases) {
        int done = times.size() - pending.size();
        for (Iterator<GpuTimer[]> it = pending.iterator(); it.hasNext(); done++) {
            GpuTimer[] t = it.next();
            long recolor = t[0].nanos(), restore = t[1].nanos();
            if (recolor < 0 || restore < 0) return;
            times.set(done, new long[]{times.get(done)[0], recolor, restore});
            close(t);
            it.remove();
        }
        if (timedLeft > 0) return;
        sink.accept(times, bytes, atlases);
        times.clear();
    }

    private static void close(GpuTimer[] timers) {
        for (GpuTimer t : timers) t.close();
    }
}
