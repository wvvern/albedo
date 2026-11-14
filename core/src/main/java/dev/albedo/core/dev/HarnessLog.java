package dev.albedo.core.dev;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/** The log file of the harness, which tools/harness/protocol.py reads line by line. */
public final class HarnessLog {
    /** The start of a line that the harness tool counts as a failure. */
    public static final String FAIL_PREFIX = "! ";

    private static volatile File logFile;

    private HarnessLog() {}

    /** Sends the next lines to {@code <dir>/log.txt}. */
    static void open(File dir) {
        logFile = new File(dir, "log.txt");
    }

    /** Appends a line to the harness log; safe from any thread, never throws. */
    public static void log(String message) {
        File f = logFile;
        if (f != null) write(f, message + System.getProperty("line.separator"), true);
    }

    /** Appends a line that the harness tool counts as a failure. */
    public static void fail(String message) {
        log(FAIL_PREFIX + message);
    }

    static synchronized void write(File f, String text, boolean append) {
        try {
            Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
            // Logging must never break the game.
        }
    }
}
