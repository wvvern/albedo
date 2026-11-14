package dev.albedo.core.sky;

/**
 * The sharp sky of the sky pass that waits for the hook after the solid world, which it does only when that hook ran in
 * the frame before, so that a renderer that skips the hook gets the sky in the sky pass again from the next frame on.
 *
 * @param <T> the transforms of the sky in an adapter
 */
public final class SkyDefer<T> {
    private boolean hookRan;
    private boolean hookRanBefore;
    private T waiting;

    /** Starts a frame: a sky that still waits goes, because its transforms are only for the frame before. */
    public void frameStart() {
        hookRanBefore = hookRan;
        hookRan = false;
        waiting = null;
    }

    /**
     * Keeps the sharp sky of the sky pass for the hook when the hook ran in the frame before, so that the sky waits for the
     * solid world, which then hides a part of it from the shader.
     *
     * @return false when the sky pass must draw the sky itself
     */
    public boolean defer(T sky) {
        waiting = hookRanBefore ? sky : null;
        return hookRanBefore;
    }

    /** The sky that waits, or null when the sky pass of this frame did not keep one; the hook after the solid world calls this. */
    public T take() {
        hookRan = true;
        T sky = waiting;
        waiting = null;
        return sky;
    }
}
