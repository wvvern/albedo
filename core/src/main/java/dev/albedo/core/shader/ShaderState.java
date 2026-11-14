package dev.albedo.core.shader;

/** The compile state of a GL program or a GPU pipeline of the mod, where a failed or closed shader never compiles again. */
public enum ShaderState {
    /** The first use starts the compile. */
    NOT_STARTED,
    /** The compile started and has no result yet. */
    RUNNING,
    /** The shader draws. */
    READY,
    /** The compile failed, or the game rejected the pipeline. */
    FAILED,
    /** The shader went free. */
    CLOSED;

    /**
     * The state after a move to next, where an illegal move keeps this state (so CLOSED stays CLOSED) and does not throw,
     * because the render thread calls it.
     */
    public ShaderState to(ShaderState next) {
        return allows(next) ? next : this;
    }

    /** True only for READY. */
    public boolean usable() {
        return this == READY;
    }

    /** True when the shader never becomes usable: FAILED or CLOSED. */
    public boolean dead() {
        return this == FAILED || this == CLOSED;
    }

    private boolean allows(ShaderState next) {
        if (next == CLOSED) return true;
        switch (this) {
            case NOT_STARTED:
                return next == RUNNING || next == READY || next == FAILED;
            case RUNNING:
                return next == READY || next == FAILED;
            case READY:
                // A precompiled pipeline goes to the game again in each frame.
                return next == READY || next == FAILED;
            default:
                return false;
        }
    }
}
