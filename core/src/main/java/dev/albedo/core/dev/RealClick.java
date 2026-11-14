package dev.albedo.core.dev;

import dev.albedo.core.HarnessHost;

import java.util.concurrent.atomic.AtomicReference;

/** Presses the button for a waiting {@code ui realclick} and releases it on the next tick, as a real click lasts some frames. */
final class RealClick {
    /** The label position of the last {@code ui realclick}, which the render thread gives, or null. */
    private final AtomicReference<int[]> waiting = new AtomicReference<int[]>();
    /** The GUI position whose button is down until the next tick, or null. */
    private int[] pressedAt;

    /** Asks for a click at a GUI position on the next tick, on any thread. */
    void request(int[] at) {
        waiting.set(at);
    }

    void tick(HarnessHost host) {
        if (pressedAt != null) {
            host.mouseButton(pressedAt[0], pressedAt[1], false);
            pressedAt = null;
            return;
        }
        int[] at = waiting.getAndSet(null);
        if (at == null) return;
        if (host.mouseButton(at[0], at[1], true)) {
            pressedAt = at;
            HarnessLog.log("realclick at " + at[0] + "," + at[1]);
        } else {
            HarnessLog.fail("realclick is not available on this version");
        }
    }
}
