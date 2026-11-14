package dev.albedo.core.ui;

/** The progress of a full resource reload: one step for each reload listener, with the texture loads of each step. */
public final class ReloadProgress {
    /** The texture loads at which the bar is half way through the part of a listener. */
    private static final float LOADS_HALF_STEP = 200f;

    private int step;
    private int steps;
    private int loads;

    public void begin(int listeners) {
        steps = listeners;
        step = 0;
    }

    /** The next reload listener starts. */
    public void step() {
        step++;
        loads = 0;
    }

    /** A texture of the current step loaded. */
    public void load() {
        loads++;
    }

    /** The part of the reload that is done, from 0 to 1. */
    public float progress() {
        if (steps <= 0) return 0f;
        // Each listener has an equal part, in which the bar moves toward its end as the textures load.
        float part = 1f / steps;
        return Math.min(1f, Math.max(0, step - 1) * part + part * loads / (loads + LOADS_HALF_STEP));
    }

    public String title() {
        return "Albedo: reloading resources";
    }

    public String detail() {
        return steps > 0 ? "Step " + Math.max(1, step) + " of " + steps : "Starting";
    }
}
