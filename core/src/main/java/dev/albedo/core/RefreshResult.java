package dev.albedo.core;

/** What {@link Albedo} did to show a new policy in the world. */
public final class RefreshResult {
    /** The path a refresh took, from cheapest to slowest. */
    public enum Outcome {
        /** No texture or chunk needed a change, because the game reads sky, fog, fullbright and shadows each frame. */
        UNCHANGED,
        /** Only the changed textures got new pixels. */
        REPAINTED,
        /** The changed textures got new pixels and the chunk meshes were built again for new tints or shading. */
        REBUILT_CHUNKS,
        /** A repaint could not reach all changes, so the game reloads all resources for the {@link #reason}. */
        RELOADED
    }

    public final Outcome outcome;
    public final int repainted;
    public final long millis;
    /** Why the refresh needs a reload, or null for the other outcomes. */
    public final String reason;

    private RefreshResult(Outcome outcome, int repainted, long millis, String reason) {
        this.outcome = outcome;
        this.repainted = repainted;
        this.millis = millis;
        this.reason = reason;
    }

    static RefreshResult painted(int repainted, boolean rebuiltChunks, long millis) {
        Outcome o = rebuiltChunks ? Outcome.REBUILT_CHUNKS : repainted > 0 ? Outcome.REPAINTED : Outcome.UNCHANGED;
        return new RefreshResult(o, repainted, millis, null);
    }

    static RefreshResult reloaded(String reason) {
        return new RefreshResult(Outcome.RELOADED, 0, 0, reason);
    }

    /** A log line, or null when nothing happened. */
    String describe() {
        switch (outcome) {
            case UNCHANGED: return null;
            case RELOADED: return "Resources reload because " + reason;
            default:
                return "Repainted " + repainted + " textures" + (outcome == Outcome.REBUILT_CHUNKS ? " and rebuilt chunks" : "")
                        + " in " + millis + " ms";
        }
    }

    @Override
    public String toString() {
        return outcome + (reason != null ? " (" + reason + ")" : " " + repainted);
    }
}
