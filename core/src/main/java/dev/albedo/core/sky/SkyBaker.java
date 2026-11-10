package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Bakes sky images of one size in the background, where the newest request wins: an older bake stops at its next row,
 * and {@link #image} keeps the last finished image until the new one is ready.
 */
public final class SkyBaker {
    /** The threads that share the rows of all bakes: one less than the processors, at low priority. */
    private static final class Pool {
        static final int SIZE = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        static final ExecutorService ROWS = executor("albedo-sky-rows", SIZE);
    }

    private final int width, height;
    private final Consumer<Throwable> errors;
    private final ExecutorService orchestrator = executor("albedo-sky-bake", 1);
    private final AtomicLong generation = new AtomicLong();
    private volatile SkyDesign requested;
    private volatile SkyImage image;

    /** @param errors receives a failure of a bake, which then keeps the last image */
    public SkyBaker(int width, int height, Consumer<Throwable> errors) {
        this.width = width;
        this.height = height;
        this.errors = errors;
    }

    /** Starts a bake of {@code design} when it differs from the last request; null clears the image. */
    public synchronized void request(SkyDesign design) {
        if (design == null) {
            requested = null;
            generation.incrementAndGet();
            image = null;
            return;
        }
        if (design.equals(requested)) return;
        SkyImage last = image;
        if (last != null && last.design().equals(requested) && design.baked().equals(requested.baked())) {
            // Only the render or the turning changed, which the bake does not read, so the finished image stays with the new design.
            requested = design;
            image = last.withDesign(design);
            return;
        }
        requested = design;
        long mine = generation.incrementAndGet();
        BooleanSupplier stale = () -> generation.get() != mine;
        orchestrator.execute(() -> {
            if (stale.getAsBoolean()) return;
            try {
                SkyImage done = SkyGenerator.bake(design, width, height, parallel(stale));
                if (done != null) publish(mine, done);
            } catch (RuntimeException | OutOfMemoryError e) {
                errors.accept(e);
            }
        });
    }

    /** Keeps a finished image only while no newer request came, under the lock of {@link #request} so that a clear stays. */
    private synchronized void publish(long mine, SkyImage done) {
        if (generation.get() == mine) image = done;
    }

    /** The last finished image, which may show an older design while a new bake runs, or null. */
    public SkyImage image() {
        return image;
    }

    /** True when the last finished image shows the last requested design. */
    boolean current() {
        SkyImage i = image;
        return i != null && i.design().equals(requested);
    }

    /** Shares the rows between the pool and the calling thread, which also works so that a busy pool cannot block it. */
    private static SkyGenerator.Rows parallel(BooleanSupplier stale) {
        return (count, row) -> {
            AtomicInteger next = new AtomicInteger();
            AtomicReference<RuntimeException> failure = new AtomicReference<RuntimeException>();
            Runnable work = () -> {
                try {
                    for (int i = next.getAndIncrement(); i < count; i = next.getAndIncrement()) {
                        if (stale.getAsBoolean()) {
                            next.set(count);
                            return;
                        }
                        row.accept(i);
                    }
                } catch (RuntimeException e) {
                    failure.set(e);
                    next.set(count);
                }
            };
            int helpers = Math.min(Pool.SIZE, count / 8);
            CountDownLatch done = new CountDownLatch(helpers);
            for (int k = 0; k < helpers; k++) {
                Pool.ROWS.execute(() -> {
                    try {
                        work.run();
                    } finally {
                        done.countDown();
                    }
                });
            }
            work.run();
            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (failure.get() != null) throw failure.get();
            return !stale.getAsBoolean();
        };
    }

    private static ExecutorService executor(String name, int threads) {
        AtomicInteger count = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), r -> {
            Thread t = new Thread(r, name + "-" + count.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
}
