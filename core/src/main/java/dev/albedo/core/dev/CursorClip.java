package dev.albedo.core.dev;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * The development-only check behind the harness command {@code cursorfree}, because a game that grabs the mouse clips the
 * cursor of the whole window station, also on the desktop of the player.
 */
final class CursorClip {
    private static final long WAIT_SECONDS = 30;

    private CursorClip() {}

    /**
     * The log line of the check, which starts with "!" when the clip is smaller than the virtual screen.
     *
     * @param numbers left, top, right and bottom of the clip, then left, top, width and height of the virtual screen
     */
    static String line(int[] numbers) {
        int clipWidth = numbers[2] - numbers[0];
        int clipHeight = numbers[3] - numbers[1];
        String shape = "clip " + numbers[0] + "," + numbers[1] + "," + numbers[2] + "," + numbers[3] + " (" + clipWidth + " x " + clipHeight
                + "), screen " + numbers[6] + " x " + numbers[7];
        if (clipWidth < numbers[6] || clipHeight < numbers[7]) return "! cursorfree: the cursor is clipped, " + shape;
        return "cursorfree: ok, " + shape;
    }

    /** Parses the output of {@code clip.ps1}: eight numbers, or null when the line is not that. */
    static int[] parse(String output) {
        String[] words = output.trim().split("\\s+");
        if (words.length != 8) return null;
        int[] numbers = new int[8];
        try {
            for (int i = 0; i < 8; i++) numbers[i] = Integer.parseInt(words[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        return numbers;
    }

    /** Reads the clip with {@code files/clip.ps1} of the harness folder and returns the log line of the check. */
    static String check(File dir) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) return "cursorfree: skipped, not Windows";
        List<String> command = new ArrayList<>();
        command.add("powershell.exe");
        command.add("-NoProfile");
        command.add("-NonInteractive");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(new File(dir, "files/clip.ps1").getPath());
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            byte[] bytes = readAll(process);
            if (!process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "! cursorfree: the clip probe did not finish";
            }
            String output = new String(bytes, StandardCharsets.UTF_8);
            int[] numbers = parse(output);
            return numbers == null ? "! cursorfree: the clip probe gave: " + output.trim() : line(numbers);
        } catch (IOException e) {
            return "! cursorfree: cannot run the clip probe: " + e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "! cursorfree: stopped";
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
