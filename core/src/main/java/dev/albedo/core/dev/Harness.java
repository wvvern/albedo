package dev.albedo.core.dev;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.Colors;
import dev.albedo.core.Platform;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.ui.SettingsEditor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Development harness, created only with {@code -Dalbedo.harness=<directory>}, that runs the lines of
 * {@code <dir>/commands.txt} so screenshots can be taken on a hidden desktop with no keyboard, mouse or sound.
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

        /**
         * Mutes sound, disables the narrator and keeps the game running without focus, and must be cheap because it
         * runs every tick.
         */
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

    /** Runs one command line, where a {@code ui} command needs a short {@code wait} after it before a screenshot. */
    private void execute(String line) {
        int space = line.indexOf(' ');
        String verb = space < 0 ? line : line.substring(0, space);
        String arg = space < 0 ? "" : line.substring(space + 1).trim();
        Albedo sc = Albedo.get();
        Config config = sc.storedConfig();
        Toggle toggle = Toggle.byKey(verb);
        if (verb.equals("world")) host.openWorld(arg);
        else if (verb.equals("waitworld")) waitingForWorld = true;
        else if (verb.equals("run")) host.runCommand(arg);
        else if (verb.equals("wait")) waitTicks = Integer.parseInt(arg);
        else if (verb.equals("mod")) sc.apply(config.withEnabled(arg.equals("on")));
        else if (verb.equals("profile")) sc.apply(config.withActiveProfile(arg).withEnabled(true));
        else if (toggle != null) sc.apply(config.withProfile(config.activeProfile().with(toggle, arg.equals("on"))));
        else if (verb.equals("group")) sc.apply(config.withProfile(withGroup(config.activeProfile(), arg.split("\\s+"))));
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

    /**
     * Sets or replaces a group by name from {@code <category> <name> <color or mode> <pattern>...}, for example
     * {@code group particles Fire #FF0000 flame lava}.
     */
    static Profile withGroup(Profile profile, String[] args) {
        if (args.length < 4) throw new IllegalArgumentException("group <category> <name> <color or mode> <pattern>...");
        Category category = Category.byKey(args[0]);
        if (category == null) throw new IllegalArgumentException("unknown category: " + args[0]);
        Rule group = new Rule(args[1], Arrays.asList(args).subList(3, args.length), look(args[2]), false);
        CategorySettings settings = profile.get(category);
        List<Rule> rules = settings.rules();
        int index = 0;
        while (index < rules.size() && !rules.get(index).name().equals(group.name())) index++;
        if (index < rules.size()) return profile.with(category, settings.withRule(index, group));
        return profile.with(category, settings.withRuleAt(rules.size(), group)); // appends a new group
    }

    /** A mode such as "hidden", or a solid color in any form that {@link Colors#parse} reads. */
    private static Appearance look(String text) {
        try {
            return Appearance.of(Appearance.Mode.byKey(text));
        } catch (IllegalArgumentException notAMode) {
            return Appearance.solid(Colors.parse(text));
        }
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
