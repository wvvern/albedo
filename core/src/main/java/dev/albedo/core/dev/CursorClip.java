package dev.albedo.core.dev;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * The development-only check behind the harness command {@code cursorfree}, because a game that grabs the mouse clips the
 * cursor of the whole window station, also on the desktop of the player.
 */
final class CursorClip {
    private static final long WAIT_SECONDS = 30;
    /** The probe prints the left, top, right and bottom of the clip, then the left, top, width and height of the virtual screen. */
    private static final int PROBE_NUMBERS = 8;
    private static final String FAILED = HarnessLog.FAIL_PREFIX + "cursorfree: ";

    /** The cursor clip and the size of the virtual screen. */
    static final class ClipProbe {
        final int clipLeft, clipTop, clipRight, clipBottom, screenWidth, screenHeight;

        ClipProbe(int clipLeft, int clipTop, int clipRight, int clipBottom, int screenWidth, int screenHeight) {
            this.clipLeft = clipLeft;
            this.clipTop = clipTop;
            this.clipRight = clipRight;
            this.clipBottom = clipBottom;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
        }

        /** The log line of the check, which starts with "!" when the clip is smaller than the virtual screen. */
        String line() {
            int clipWidth = clipRight - clipLeft;
            int clipHeight = clipBottom - clipTop;
            String shape = "clip " + clipLeft + "," + clipTop + "," + clipRight + "," + clipBottom + " (" + clipWidth + " x " + clipHeight
                    + "), screen " + screenWidth + " x " + screenHeight;
            if (clipWidth < screenWidth || clipHeight < screenHeight) return FAILED + "the cursor is clipped, " + shape;
            return "cursorfree: ok, " + shape;
        }
    }

    private CursorClip() {}

    /** Parses the output of the clip probe, or returns null when the line is not {@link #PROBE_NUMBERS} numbers. */
    static ClipProbe parse(String output) {
        String[] words = output.trim().split("\\s+");
        if (words.length != PROBE_NUMBERS) return null;
        int[] n = new int[PROBE_NUMBERS];
        try {
            for (int i = 0; i < PROBE_NUMBERS; i++) n[i] = Integer.parseInt(words[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        return new ClipProbe(n[0], n[1], n[2], n[3], n[6], n[7]);
    }

    /**
     * Reads the clip with the probe of the harness folder and returns the log line of the check.
     *
     * <p>{@code clip-probe.txt} has the command line of the probe, one argument in each line, from the tool that started the game.
     */
    static String check(File dir) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) return "cursorfree: skipped, not Windows";
        try {
            List<String> command = Files.readAllLines(new File(dir, "clip-probe.txt").toPath(), StandardCharsets.UTF_8);
            if (command.isEmpty()) return FAILED + "clip-probe.txt has no command";
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            // The wait comes before the read, because a read of a probe that hangs has no end; the one line of the probe fits in the pipe.
            if (!process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return FAILED + "the clip probe did not finish";
            }
            String output = new String(readAll(process), StandardCharsets.UTF_8);
            ClipProbe probe = parse(output);
            return probe == null ? FAILED + "the clip probe gave: " + output.trim() : probe.line();
        } catch (IOException e) {
            return FAILED + "cannot run the clip probe: " + e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FAILED + "stopped";
        }
    }

    private static byte[] readAll(Process process) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int n;
        InputStream in = process.getInputStream();
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
