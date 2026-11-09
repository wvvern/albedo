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
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.sky.SkyPresets;
import dev.albedo.core.ui.SettingsEditor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
         * Loads a PNG as a downloaded skin or cape through the path of the game, from its skin cache, so that nothing
         * goes to the network.
         *
         * @param hash the texture hash that the game would get from the profile, which names the cache file
         * @return the name of the texture, for example "minecraft:skins/<sha1>"
         */
        String loadSkin(File png, String hash, boolean cape) throws IOException;

        /** Releases a texture as the game does, for example "minecraft:skins/<sha1>". */
        void releaseTexture(String id);

        /** Opens a screen that shows a texture large, where "steve" is the default skin. */
        void showTexture(String id);

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
    /** The last texture that {@code skin} loaded. */
    private String lastSkin;

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
        else if (verb.equals("effects")) sc.apply(config.withProfile(withEffects(config.activeProfile(), arg.split("\\s+"))));
        else if (verb.equals("sky")) sc.apply(config.withProfile(withSky(config.activeProfile(), arg)));
        else if (verb.equals("settings")) {
            if (arg.equals("open")) platform.openSettings();
            else host.closeScreen();
        } else if (verb.equals("ui")) SettingsEditor.script(arg);
        else if (verb.equals("hud")) host.setHudVisible(arg.equals("on"));
        else if (verb.equals("reload")) platform.reloadResources();
        else if (verb.equals("screenshot")) host.screenshot(new File(dir, arg.endsWith(".png") ? arg : arg + ".png"));
        else if (verb.equals("skin")) skin(arg);
        else if (verb.equals("release")) host.releaseTexture(textureArg(arg));
        else if (verb.equals("overlay") && arg.equals("off")) host.closeScreen();
        else if (verb.equals("overlay")) host.showTexture(textureArg(arg));
        else if (verb.equals("quit")) host.quit();
        else log("! unknown command: " + verb);
    }

    /** Loads {@code <png> [cape]} from the harness folder as a downloaded skin or cape. */
    private void skin(String arg) {
        String[] args = arg.split("\\s+");
        boolean cape = args.length > 1 && args[1].equals("cape");
        File png = new File(dir, args[0]);
        try {
            lastSkin = host.loadSkin(png, skinHash(Files.readAllBytes(png.toPath()), cape), cape);
            log("skin " + lastSkin);
        } catch (IOException e) {
            log("! cannot load skin " + png + ": " + e);
        }
    }

    /** A texture id, where "skin" or no text is the last skin that {@code skin} loaded. */
    private String textureArg(String arg) {
        if (!arg.isEmpty() && !arg.equals("skin")) return arg;
        if (lastSkin == null) throw new IllegalStateException("no skin: run skin <png> first");
        return lastSkin;
    }

    /** A hash for the cache file of a skin, from its pixels and type, as the skin server gives one for each image. */
    static String skinHash(byte[] png, boolean cape) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(png);
            sha.update((byte) (cape ? 1 : 0));
            StringBuilder hex = new StringBuilder();
            for (byte b : sha.digest()) hex.append(String.format("%02x", b & 0xFF));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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

    /** Sets the effects of a category's look from {@code <category> [bevel N] [outline N] [chroma N]}, where a missing effect is off. */
    static Profile withEffects(Profile profile, String[] args) {
        Category category = Category.byKey(args[0]);
        if (category == null) throw new IllegalArgumentException("unknown category: " + args[0]);
        if (args.length % 2 == 0) throw new IllegalArgumentException("effects <category> [bevel N] [outline N] [chroma N]");
        int bevel = 0, outline = 0, chroma = 0;
        for (int i = 1; i < args.length; i += 2) {
            int value = Integer.parseInt(args[i + 1]);
            if (args[i].equals("bevel")) bevel = value;
            else if (args[i].equals("outline")) outline = value;
            else if (args[i].equals("chroma")) chroma = value;
            else throw new IllegalArgumentException("unknown effect: " + args[i]);
        }
        CategorySettings settings = profile.get(category);
        return profile.with(category, settings.withAppearance(settings.appearance().withEffects(bevel, outline, chroma)));
    }

    /** Sets the sky to a preset of custom layers ({@code sky dusk}), or back to the vanilla sky with {@code sky off}. */
    static Profile withSky(Profile profile, String name) {
        CategorySettings sky = profile.get(Category.SKY);
        if (name.equals("off")) return profile.with(Category.SKY, sky.withAppearance(Appearance.ORIGINAL));
        SkyDesign design = SkyPresets.byName(name);
        if (design == null) throw new IllegalArgumentException("sky <" + String.join("|", SkyPresets.all().keySet()) + "|off>");
        return profile.with(Category.SKY, sky.withAppearance(Appearance.of(Appearance.Mode.LAYERS)).withSky(design));
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
