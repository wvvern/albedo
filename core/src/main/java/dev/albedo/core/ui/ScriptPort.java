package dev.albedo.core.ui;

/** The dev harness as the settings screen sees it: the source of the {@code ui} commands and the sink of their log lines. */
public interface ScriptPort {
    /** Whether a command waits, which {@link #poll} does not take yet. */
    boolean waiting();

    /** Takes the next command, or returns null when none waits. */
    String poll();

    void log(String line);

    /** Logs a line that the harness tool counts as a failure. */
    void fail(String reason);

    /** Gives the GUI position {x, y} of a {@code realclick}, which the harness clicks through the game's mouse handler. */
    void realClick(int[] at);
}
