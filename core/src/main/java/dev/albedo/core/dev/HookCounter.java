package dev.albedo.core.dev;

import java.util.concurrent.atomic.LongAdder;

/** Counts the runs of a hook that can miss its target with no error, so that the harness command "hooks" shows a hook that never ran. */
public final class HookCounter {
    private final String name;
    private final LongAdder runs = new LongAdder();

    /** @param name the name of the hook in the harness log, for example "afterSolid" */
    public HookCounter(String name) {
        this.name = name;
    }

    /** Records one run of the hook, from any thread. */
    public void run() {
        runs.increment();
    }

    public String name() {
        return name;
    }

    public long runs() {
        return runs.sum();
    }
}
