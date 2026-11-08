package dev.albedo.core.dev;

import dev.albedo.core.Albedo;
import dev.albedo.core.Platform;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.ui.SettingsEditor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Development harness: drives the game from a command file so screenshots can be taken on a hidden desktop with no
 * keyboard, mouse or sound. Enabled only with {@code -Dalbedo.harness=<directory>}; otherwise never created.
 *
 * <p>Write commands, one per line, to {@code <dir>/commands.txt}. The harness takes the file, runs the lines in
 * order and appends progress to {@code <dir>/log.txt}; {@code <dir>/status.txt} holds {@code menu} or {@code world}.
 * <ul>
 *   <li>{@code world <name>}: opens the save, creating a creative superflat world if it doesn't exist</li>
 *   <li>{@code waitworld}: pauses until a world is loaded</li>
 *   <li>{@code run <command>}: a server command with operator rights, e.g. {@code run time set 6000}</li>
 *   <li>{@code mod on|off}, {@code profile <name>}, {@code <toggle> on|off} (fullbright, shading, foliage, shadows):
 *       change the config like a player would</li>
 *   <li>{@code settings open|close}, {@code hud on|off}, {@code reload} (all resources, as F3+T)</li>
 *   <li>{@code ui click <label>|type <text>|key <UiKey>|pointer off}: input for the open settings screen, to show
 *       each page (see {@link SettingsEditor#script}); put a short {@code wait} after each</li>
 *   <li>{@code wait <ticks>}, {@code screenshot <name>}, {@code quit}</li>
 * </ul>
 */
public final class Harness {
    /** What the harness needs from the game; implemented next to each version's adapter. */
    public interface Host {
        boolean inWorld();

        void openWorld(String name);

        void runCommand(String command);

        /** Writes the next rendered frame to {@code png}, then calls {@link Harness#log}. */
        void screenshot(File png);

        void setHudVisible(boolean visible);

        void closeScreen();

        void quit();

        /** Mute sound, disable the narrator, keep running without focus. Called every tick; must be cheap. */
        void quiet();
    }

    private static volatile File logFile;

    private final File dir;
    private final Platform platform;
    private final Host host;
    private final Deque<String> queue = new ArrayDeque<String>();
    private int waitTicks;
    private boolean waitingForWorld;
    private String lastStatus = "";

    private Harness(File dir, Platform platform) {
        this.dir = dir;
        this.platform = platform;
        this.host = platform.harnessHost();
    }

    /** @return the harness, or null when the system property is not set */
    public static Harness fromSystemProperty(Platform platform) {
        String path = System.getProperty("albedo.harness");
        if (path == null || path.trim().isEmpty()) return null;
        File dir = new File(path).getAbsoluteFile();
        dir.mkdirs();
        logFile = new File(dir, "log.txt");
        platform.info("Test harness enabled in " + dir);
        return new Harness(dir, platform);
    }

    public void tick() {
        host.quiet();
        writeStatus(host.inWorld() ? "world" : "menu");
        takeCommandFile();
        if (waitingForWorld) {
            if (!host.inWorld()) return;
            waitingForWorld = false;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        while (!queue.isEmpty() && waitTicks == 0 && !waitingForWorld) {
            String line = queue.poll().trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            log("> " + line);
            try {
                execute(line);
            } catch (RuntimeException e) {
                log("! " + e);
            }
        }
    }

    private void execute(String line) {
        int space = line.indexOf(' ');
        String verb = space < 0 ? line : line.substring(0, space);
        String arg = space < 0 ? "" : line.substring(space + 1).trim();
        Albedo sc = Albedo.get();
        Config config = sc.config();
        Toggle toggle = Toggle.byKey(verb);
        if (verb.equals("world")) host.openWorld(arg);
        else if (verb.equals("waitworld")) waitingForWorld = true;
        else if (verb.equals("run")) host.runCommand(arg);
        else if (verb.equals("wait")) waitTicks = Integer.parseInt(arg);
        else if (verb.equals("mod")) sc.apply(config.withEnabled(arg.equals("on")));
        else if (verb.equals("profile")) sc.apply(config.withActiveProfile(arg).withEnabled(true));
        else if (toggle != null) sc.apply(config.withProfile(config.activeProfile().with(toggle, arg.equals("on"))));
        else if (verb.equals("settings")) {
            if (arg.equals("open")) platform.openSettings();
            else host.closeScreen();
        } else if (verb.equals("ui")) SettingsEditor.script(arg);
        else if (verb.equals("hud")) host.setHudVisible(arg.equals("on"));
        else if (verb.equals("reload")) platform.reloadResources();
        else if (verb.equals("screenshot")) host.screenshot(new File(dir, arg.endsWith(".png") ? arg : arg + ".png"));
        else if (verb.equals("quit")) host.quit();
        else log("! unknown command: " + verb);
    }

    private void takeCommandFile() {
        File commands = new File(dir, "commands.txt");
        // Leave a file that is still being written alone; taking it early queues only part of it.
        if (!commands.isFile() || System.currentTimeMillis() - commands.lastModified() < 500) return;
        File taken = new File(dir, "commands.taken");
        taken.delete();
        if (!commands.renameTo(taken)) return; // the writer may still hold it; try next tick
        try {
            queue.addAll(Files.readAllLines(taken.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            log("! cannot read commands: " + e);
        }
    }

    private void writeStatus(String status) {
        if (status.equals(lastStatus)) return;
        lastStatus = status;
        write(new File(dir, "status.txt"), status, false);
    }

    /** Appends to the harness log; safe from any thread, never throws. */
    public static void log(String message) {
        File f = logFile;
        if (f != null) write(f, message + System.getProperty("line.separator"), true);
    }

    private static synchronized void write(File f, String text, boolean append) {
        try {
            Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
            // Logging must never break the game.
        }
    }
}
