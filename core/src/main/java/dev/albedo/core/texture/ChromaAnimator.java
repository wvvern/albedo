package dev.albedo.core.texture;

import dev.albedo.core.Policy;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Moves the Chroma colors a little on each client tick: it repaints the textures whose color step changed until the
 * time budget, which includes the upload to the GPU, is spent.
 */
public final class ChromaAnimator {
    /** The time that one tick can spend on Chroma, paint and upload together. */
    static final long BUDGET_NANOS = 2_000_000L;
    /** The upload time of one texture until a measurement replaces it. */
    private static final double FIRST_UPLOAD_NANOS = 20_000;
    /** How much each new measurement changes the mean upload time. */
    private static final double UPLOAD_WEIGHT = 0.2;
    /** The number of ticks with moving colors in one {@link #report}. */
    static final int REPORT_TICKS = 200;

    /** What one tick did, with its times in nanoseconds. */
    public static final class Tick {
        static final Tick IDLE = new Tick(0, 0, 0, 0, 0, true);

        public final int moving;
        public final int repainted;
        public final int failed;
        public final long paintNanos;
        public final long uploadNanos;
        /** False when the adapter could not send the uploads to the GPU, which stops the colors until the next settings change. */
        public final boolean uploaded;

        Tick(int moving, int repainted, int failed, long paintNanos, long uploadNanos, boolean uploaded) {
            this.moving = moving;
            this.repainted = repainted;
            this.failed = failed;
            this.paintNanos = paintNanos;
            this.uploadNanos = uploadNanos;
            this.uploaded = uploaded;
        }
    }

    private final LongSupplier nanoClock;
    /** The mean upload time of one texture, which the budget keeps free for the upload of each repaint. */
    private double uploadNanos = FIRST_UPLOAD_NANOS;
    /** The policy under which an upload to the GPU failed, or null. */
    private Policy stopped;

    private final long[] paintTimes = new long[REPORT_TICKS];
    private final long[] uploadTimes = new long[REPORT_TICKS];
    private final long[] totalTimes = new long[REPORT_TICKS];
    private int reportTicks;
    private long reportRepainted;
    private int reportMoving;

    /** @param nanoClock a monotonic clock in nanoseconds, such as {@code System::nanoTime} */
    public ChromaAnimator(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    /** The time in milliseconds that gives the Chroma steps, the same for every thread. */
    public long timeMs() {
        return Math.floorDiv(nanoClock.getAsLong(), 1_000_000L);
    }

    /**
     * Repaints textures inside the budget, then sends them to the GPU with {@code flush}.
     *
     * @param flush sends the uploads of the repaint to the GPU, and returns false when it cannot
     */
    public Tick tick(TextureStore store, Policy policy, BooleanSupplier flush) {
        if (policy == stopped) return Tick.IDLE;
        long start = nanoClock.getAsLong();
        long timeMs = Math.floorDiv(start, 1_000_000L);
        TextureStore.Step step = store.animate(policy, timeMs,
                n -> n == 0 || nanoClock.getAsLong() - start + (n + 1) * uploadNanos <= BUDGET_NANOS);
        long painted = nanoClock.getAsLong();
        boolean uploaded = true;
        long upload = 0;
        if (step.repainted > 0 || step.failed > 0) {
            uploaded = flush.getAsBoolean();
            upload = nanoClock.getAsLong() - painted;
            if (step.repainted > 0) uploadNanos += UPLOAD_WEIGHT * ((double) upload / step.repainted - uploadNanos);
            if (!uploaded) stopped = policy;
        }
        Tick tick = new Tick(step.moving, step.repainted, step.failed, painted - start, upload, uploaded);
        if (tick.moving > 0) count(tick);
        return tick;
    }

    /** Adds a tick with moving colors to the next {@link #report}. */
    private void count(Tick t) {
        paintTimes[reportTicks] = t.paintNanos;
        uploadTimes[reportTicks] = t.uploadNanos;
        totalTimes[reportTicks] = t.paintNanos + t.uploadNanos;
        reportRepainted += t.repainted;
        reportMoving = t.moving;
        reportTicks++;
    }

    /** A log line with the time of each tick over the last {@link #REPORT_TICKS} ticks with moving colors, or null until then. */
    public String report() {
        if (reportTicks < REPORT_TICKS) return null;
        String line = String.format(Locale.ROOT,
                "Chroma tick: %d moving textures, %.1f repainted per tick, total p50 %.2f ms, p95 %.2f ms, max %.2f ms"
                        + " (paint p95 %.2f ms, upload p95 %.2f ms) over %d ticks",
                reportMoving, (double) reportRepainted / reportTicks, percentile(totalTimes, 50), percentile(totalTimes, 95),
                percentile(totalTimes, 100), percentile(paintTimes, 95), percentile(uploadTimes, 95), reportTicks);
        reportTicks = 0;
        reportRepainted = 0;
        return line;
    }

    /** The {@code p}th percentile of nanosecond times, in milliseconds. */
    static double percentile(long[] nanos, int p) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        int index = Math.min(sorted.length - 1, Math.max(0, (int) Math.ceil(p / 100.0 * sorted.length) - 1));
        return sorted[index] / 1e6;
    }
}
