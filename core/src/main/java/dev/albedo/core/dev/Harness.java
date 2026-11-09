package dev.albedo.core.dev;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.Colors;
import dev.albedo.core.Platform;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.sky.Oklab;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyDayTime;
import dev.albedo.core.sky.SkyGenerator;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyMeteors;
import dev.albedo.core.sky.SkyPresets;
import dev.albedo.core.sky.SkyProgram;
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
import java.util.Locale;

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

        /**
         * Moves the pointer to a GUI position and presses or releases the left button through the game's own mouse handler,
         * as a real mouse does, and returns false when this version cannot do that.
         */
        boolean mouseButton(int guiX, int guiY, boolean down);

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
         * Mutes sound, disables the narrator, keeps the game running without focus and keeps the camera where the harness
         * aims it, and must be cheap because it runs every tick.
         */
        void quiet();

        /** Sets the field of view in degrees, in the range of the game option. */
        void setFov(int degrees);

        /** Turns the camera of the player at once to a yaw and a pitch in degrees, with no command to the server. */
        void look(float yaw, float pitch);

        /**
         * Draws the program and the term images of a sky with the sharp sky shader {@code repeat} times into an
         * equirectangular image of this size, and later gives its pixels and the GPU time of one draw to {@code done}.
         *
         * @param time the time in seconds of the layers that move, or {@link SkyProgram#STILL}
         * @param png a file for the image, or null
         * @return false while the shader compiles, or when this version or GPU has no sharp sky
         */
        default boolean drawSharpSky(SkyImage sky, float time, int width, int height, int repeat, File png, SkyDone done) {
            return false;
        }

        /**
         * Measures the GPU and CPU time of the GPU pass of Chroma in each of the next frames, then logs the times, the
         * atlas sizes and the GPU memory of the pass.
         *
         * @return false when this version has no GPU pass of Chroma, or the pass does not run now
         */
        default boolean timeChroma(int frames) {
            return false;
        }
    }

    /** Receives the image of {@link Host#drawSharpSky}. */
    public interface SkyDone {
        /** @param gpuNanos the GPU time of one draw in nanoseconds, or -1 when the GPU did not give it */
        void accept(int[] argb, long gpuNanos);
    }

    /** The size of the image that {@code skycompare} draws on the GPU and bakes on the CPU. */
    private static final int COMPARE_W = 512, COMPARE_H = 256;
    /** The size and the count of the draws that {@code skytime} measures, about the pixels of the harness window. */
    private static final int TIME_W = 1600, TIME_H = 900, TIME_REPEAT = 20;
    /** The ticks that a sky command waits for the shader to compile. */
    private static final int SKY_TRIES = 200;
    /** The lowest elevation in degrees of the meteor that {@code meteor} looks at. */
    private static final int METEOR_ELEVATION = 30;
    /** The largest mean OKLab distance between the GPU and the CPU sky that {@code skycompare} accepts. */
    static final double COMPARE_LIMIT = 0.02;
    /** The number of frames that {@code chromatime} measures. */
    private static final int CHROMA_FRAMES = 300;

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
    /** The GUI position of a {@code ui realclick} whose button is down until the next tick, or null. */
    private int[] pressedAt;
    /** The CPU bake of the last {@code skycompare}, which its tries share. */
    private SkyImage compareCpu;
    /** The screenshots that the running {@code frames} command still takes, its name and the index of the next one. */
    private int burstLeft;
    private String burstName;
    private int burstIndex;

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
        realClick();
        if (waitingForWorld) {
            if (!host.inWorld()) return;
            waitingForWorld = false;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (burstLeft > 0) return; // the next commands wait for the screenshots of a frames command
        while (!queue.isEmpty() && waitTicks == 0 && !waitingForWorld && burstLeft == 0) {
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

    /** Presses the button for a waiting {@code ui realclick} and releases it on the next tick, as a real click lasts some frames. */
    private void realClick() {
        if (pressedAt != null) {
            host.mouseButton(pressedAt[0], pressedAt[1], false);
            pressedAt = null;
            return;
        }
        int[] at = SettingsEditor.takeRealClick();
        if (at == null) return;
        if (host.mouseButton(at[0], at[1], true)) {
            pressedAt = at;
            log("realclick at " + at[0] + "," + at[1]);
        } else {
            log("! realclick is not available on this version");
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
        else if (verb.equals("skyadd")) sc.apply(config.withProfile(withSkyLayer(config.activeProfile(), arg)));
        else if (verb.equals("skyrender")) sc.apply(config.withProfile(withSkyRender(config.activeProfile(), arg)));
        else if (verb.equals("skyextent")) sc.apply(config.withProfile(withSkyExtent(config.activeProfile(), arg)));
        else if (verb.equals("skyturn")) sc.apply(config.withProfile(withSkyTurning(config.activeProfile(), arg.split("\\s+"))));
        else if (verb.equals("skycompare")) skyCompare(arg.isEmpty() ? SKY_TRIES : Integer.parseInt(arg));
        else if (verb.equals("skytime")) skyTime(arg.isEmpty() ? SKY_TRIES : Integer.parseInt(arg));
        else if (verb.equals("skyclock")) skyClock(arg);
        else if (verb.equals("skypartial")) skyPartial(arg);
        else if (verb.equals("frames")) frames(arg.split("\\s+"));
        else if (verb.equals("chromagpu")) sc.chromaGpu(arg.equals("on"));
        else if (verb.equals("chromatime")) chromaTime(arg.isEmpty() ? CHROMA_FRAMES : Integer.parseInt(arg));
        else if (verb.equals("meteor")) meteor(arg.isEmpty() ? METEOR_ELEVATION : Integer.parseInt(arg));
        else if (verb.equals("fov")) host.setFov(Integer.parseInt(arg));
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

    /**
     * Sets the effects of a category's look from
     * {@code <category> [bevel N] [outline N] [chroma N] [speed N] [saturation N] [brightness N]}, where a missing
     * effect is off and a missing Chroma option has its default.
     */
    static Profile withEffects(Profile profile, String[] args) {
        Category category = Category.byKey(args[0]);
        if (category == null) throw new IllegalArgumentException("unknown category: " + args[0]);
        if (!category.hasChroma()) throw new IllegalArgumentException("this category has no effects");
        if (args.length % 2 == 0) {
            throw new IllegalArgumentException("effects <category> [bevel N] [outline N] [chroma N] [speed N] [saturation N] [brightness N]");
        }
        int bevel = 0, outline = 0, chroma = 0;
        int speed = Chroma.DEFAULT_SPEED, saturation = Chroma.DEFAULT_SATURATION, brightness = Chroma.DEFAULT_BRIGHTNESS;
        for (int i = 1; i < args.length; i += 2) {
            int value = Integer.parseInt(args[i + 1]);
            if (args[i].equals("bevel")) bevel = value;
            else if (args[i].equals("outline")) outline = value;
            else if (args[i].equals("chroma")) chroma = value;
            else if (args[i].equals("speed")) speed = value;
            else if (args[i].equals("saturation")) saturation = value;
            else if (args[i].equals("brightness")) brightness = value;
            else throw new IllegalArgumentException("unknown effect: " + args[i]);
        }
        if ((bevel > 0 || outline > 0) && !category.hasEdgeEffects()) {
            throw new IllegalArgumentException("only blocks and fluids have bevel and outline");
        }
        CategorySettings settings = profile.get(category);
        Chroma c = new Chroma(chroma, speed, saturation, brightness);
        return profile.with(category, settings.withAppearance(settings.appearance().withEffects(bevel, outline, c)));
    }

    /**
     * Sets the sky to a preset of custom layers ({@code sky dusk}), to only its lowest layers ({@code sky galaxy 4}), or
     * back to the vanilla sky with {@code sky off}.
     */
    static Profile withSky(Profile profile, String arg) {
        CategorySettings sky = profile.get(Category.SKY);
        if (arg.equals("off")) return profile.with(Category.SKY, sky.withAppearance(Appearance.ORIGINAL));
        String[] parts = arg.trim().split("\\s+");
        SkyDesign design = SkyPresets.byName(parts[0]);
        if (design == null || parts.length > 2) {
            throw new IllegalArgumentException("sky <" + String.join("|", SkyPresets.all().keySet()) + "|off> [layers]");
        }
        if (parts.length == 2) {
            int keep = Integer.parseInt(parts[1]);
            if (keep < 1 || keep > design.layers().size()) throw new IllegalArgumentException("layers: 1 to " + design.layers().size());
            design = design.withLayers(design.layers().subList(0, keep));
        }
        return profile.with(Category.SKY, sky.withAppearance(Appearance.of(Appearance.Mode.LAYERS)).withSky(design));
    }

    /** Adds a layer of a kind with its default values at the top of the custom sky ({@code skyadd posterize}). */
    static Profile withSkyLayer(Profile profile, String kind) {
        CategorySettings sky = profile.get(Category.SKY);
        if (sky.sky().layers().size() >= SkyDesign.MAX_LAYERS) {
            throw new IllegalArgumentException("the sky has " + SkyDesign.MAX_LAYERS + " layers");
        }
        SkyLayer.Kind k = SkyLayer.Kind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        return profile.with(Category.SKY, sky.withSky(sky.sky().withAdded(k)));
    }

    /** Sets the render of the custom sky to {@code sharp} or {@code fast}. */
    static Profile withSkyRender(Profile profile, String render) {
        CategorySettings sky = profile.get(Category.SKY);
        SkyDesign.Render r = SkyDesign.Render.valueOf(render.trim().toUpperCase(Locale.ROOT));
        return profile.with(Category.SKY, sky.withSky(sky.sky().withRender(r)));
    }

    /** Sets the extent of the custom sky to {@code sphere} ("Full sphere" on) or {@code horizon} (off). */
    static Profile withSkyExtent(Profile profile, String extent) {
        CategorySettings sky = profile.get(Category.SKY);
        SkyDesign.Extent e = SkyDesign.Extent.valueOf(extent.trim().toUpperCase(Locale.ROOT));
        return profile.with(Category.SKY, sky.withSky(sky.sky().withExtent(e)));
    }

    /**
     * Sets the turning of the custom sky from {@code <percent> [speed N] [height N] [pole N] [trails N] [light N] [reverse] [real]},
     * where a missing option keeps its default ({@code skyturn 100 speed 10 trails 45 real}).
     */
    static Profile withSkyTurning(Profile profile, String[] args) {
        SkyTurning t = SkyTurning.NONE.withAmount(Integer.parseInt(args[0]));
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("reverse")) t = t.withDirection(SkyTurning.Direction.REVERSE);
            else if (args[i].equals("real")) t = t.withClock(SkyTurning.Clock.REAL);
            else if (i + 1 >= args.length) {
                throw new IllegalArgumentException("skyturn <percent> [speed N] [height N] [pole N] [trails N] [light N] [reverse] [real]");
            } else if (args[i].equals("speed")) t = t.withSpeed(Integer.parseInt(args[++i]));
            else if (args[i].equals("height")) t = t.withPoleHeight(Integer.parseInt(args[++i]));
            else if (args[i].equals("pole")) t = t.withPole(Integer.parseInt(args[++i]));
            else if (args[i].equals("trails")) t = t.withTrails(Integer.parseInt(args[++i]));
            else if (args[i].equals("light")) t = t.withTrailLight(Integer.parseInt(args[++i]));
            else throw new IllegalArgumentException("unknown skyturn option: " + args[i]);
        }
        CategorySettings sky = profile.get(Category.SKY);
        return profile.with(Category.SKY, sky.withSky(sky.sky().withTurning(t)));
    }

    /**
     * Bakes the current sky design on the CPU, draws the same program with the sharp sky shader, and logs how far apart
     * the two images are; while the shader compiles, it runs again on the next tick.
     */
    private void skyCompare(int tries) {
        SkyImage world = Albedo.skyImage();
        if (world == null || !world.program().drawsSharp()) {
            log("! skycompare: the sky is not a custom sky that the shader can draw");
            return;
        }
        if (compareCpu == null || !compareCpu.design().equals(world.design())) compareCpu = SkyGenerator.bake(world.design(), COMPARE_W, COMPARE_H);
        SkyImage cpu = compareCpu;
        boolean started = host.drawSharpSky(cpu, SkyProgram.STILL, COMPARE_W, COMPARE_H, 1, new File(dir, "sky-gpu.png"), (gpu, nanos) -> {
            double[] d = difference(cpu.argb(), gpu);
            log(String.format(Locale.ROOT, "skycompare %dx%d: mean %.4f, 99%% %.4f, max %.4f: %s", COMPARE_W, COMPARE_H, d[0], d[1], d[2],
                    d[0] < COMPARE_LIMIT ? "same" : "DIFFERENT"));
        });
        if (!started) again("skycompare", tries);
    }

    /** Draws the world sky with the sharp sky shader at about the size of the harness window and logs the GPU time of one draw. */
    private void skyTime(int tries) {
        SkyImage world = Albedo.skyImage();
        if (world == null || !world.program().drawsSharp()) {
            log("! skytime: the sky is not a custom sky that the shader can draw");
            return;
        }
        boolean started = host.drawSharpSky(world, SkyClock.seconds(), TIME_W, TIME_H, TIME_REPEAT, null, (gpu, nanos) ->
                log(nanos < 0 ? "! skytime: the GPU gave no time"
                        : String.format(Locale.ROOT, "skytime %dx%d: %.3f ms on the GPU for each draw", TIME_W, TIME_H, nanos / 1e6)));
        if (!started) again("skytime", tries);
    }

    /** Asks the adapter to measure the GPU pass of Chroma in the next frames, and logs when this version cannot. */
    private void chromaTime(int frames) {
        if (!host.timeChroma(frames)) log("! chromatime: this version has no GPU pass of Chroma, or the pass does not run");
    }

    /**
     * Logs the times of the GPU pass of Chroma that an adapter measured for {@code chromatime}.
     *
     * @param cpu the CPU time of the pass in each frame, in nanoseconds
     * @param gpuStart the GPU time of the copy and the draws at the start of each frame, in nanoseconds
     * @param gpuEnd the GPU time of the copy back at the end of each frame, in nanoseconds
     * @param bytes the GPU memory of the pass
     * @param atlases the names of the atlases that the pass turns
     */
    public static void logChromaTimes(long[] cpu, long[] gpuStart, long[] gpuEnd, long bytes, String atlases) {
        long[] gpu = new long[cpu.length];
        for (int i = 0; i < gpu.length; i++) gpu[i] = gpuStart[i] + gpuEnd[i];
        log(String.format(Locale.ROOT, "chromatime: %d frames, GPU p50 %.3f ms, p95 %.3f ms, max %.3f ms (start p50 %.3f ms, end p50 %.3f ms),"
                        + " CPU p50 %.3f ms, p95 %.3f ms, GPU memory %.1f MiB for %s",
                gpu.length, ms(gpu, 50), ms(gpu, 95), ms(gpu, 100), ms(gpuStart, 50), ms(gpuEnd, 50), ms(cpu, 50), ms(cpu, 95),
                bytes / 1048576.0, atlases));
    }

    /** The {@code p}th percentile of nanosecond times, in milliseconds. */
    static double ms(long[] nanos, int p) {
        if (nanos.length == 0) return Double.NaN;
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        int index = Math.min(sorted.length - 1, Math.max(0, (int) Math.ceil(p / 100.0 * sorted.length) - 1));
        return sorted[index] / 1e6;
    }

    /** Starts {@code frames <count> <name>}: a screenshot at the end of each of the next frames, with the clock time of each. */
    private void frames(String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("frames <count> <name>");
        burstLeft = Integer.parseInt(args[0]);
        burstName = args[1];
        burstIndex = 0;
    }

    /**
     * Takes the next screenshot of a {@code frames} command; the adapter calls it on the render thread at the end of each
     * frame, after the frame is drawn.
     *
     * @param seconds the clock time that the Chroma colors of this frame have
     */
    public void frame(double seconds) {
        if (burstLeft <= 0) return;
        String name = String.format(Locale.ROOT, "%s-%02d.png", burstName, burstIndex++);
        log(String.format(Locale.ROOT, "frame %s at %.5f s", name, seconds));
        host.screenshot(new File(dir, name));
        burstLeft--;
    }

    /**
     * Holds the clock of the moving sky layers still at a time in seconds ({@code skyclock 12.5}), moves it by some
     * seconds ({@code skyclock add -0.2}), or lets it run ({@code skyclock run}).
     */
    private static void skyClock(String arg) {
        if (arg.equals("run")) SkyClock.run();
        else if (arg.startsWith("add ")) SkyClock.hold(SkyClock.seconds() + Float.parseFloat(arg.substring(4).trim()));
        else SkyClock.hold(Float.parseFloat(arg));
    }

    /** Holds the frames at a partial tick between the last two ticks ({@code skypartial 0.5}), or lets them move ({@code skypartial run}). */
    private static void skyPartial(String arg) {
        if (arg.equals("run")) SkyDayTime.run();
        else SkyDayTime.hold(Float.parseFloat(arg));
    }

    /**
     * Holds the clock at the next meteor of the world sky that is at its brightest above an elevation in degrees, and
     * turns the camera to its head, so that a screenshot shows a meteor in flight.
     */
    private void meteor(int elevation) {
        SkyImage world = Albedo.skyImage();
        float[] peak = world == null ? null : SkyMeteors.nextPeak(world.program(), SkyClock.seconds(), elevation);
        if (peak == null) {
            log("! meteor: the sky has no meteors");
            return;
        }
        SkyClock.hold(peak[0]);
        // The camera yaw is 0 to the south and 90 to the west, and a negative pitch looks up.
        double yaw = Math.toDegrees(Math.atan2(peak[1], -peak[3])) + 180;
        double pitch = -Math.toDegrees(Math.asin(peak[2]));
        log(String.format(Locale.ROOT, "meteor at %.3f s, yaw %.1f, pitch %.1f", peak[0], yaw, pitch));
        // A tp command with a relative place does not turn a falling player on 1.8.9, so the client turns the camera.
        host.look((float) yaw, (float) pitch);
    }

    /** Runs a sky command again on the next tick, until no tries are left. */
    private void again(String verb, int tries) {
        if (tries <= 0) {
            log("! " + verb + ": this version has no sharp sky shader, or it does not compile");
            return;
        }
        queue.addFirst(verb + " " + (tries - 1));
        waitTicks = 1;
    }

    /**
     * The mean, the 99th percentile and the largest OKLab distance between the pixels of two images of the same size.
     *
     * @return {mean, 99th percentile, largest}
     */
    static double[] difference(int[] a, int[] b) {
        if (a.length != b.length || a.length == 0) throw new IllegalArgumentException("the images have different sizes");
        double[] d = new double[a.length];
        float[] la = new float[3], lb = new float[3];
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            lab(a[i], la);
            lab(b[i], lb);
            d[i] = Math.sqrt((la[0] - lb[0]) * (la[0] - lb[0]) + (la[1] - lb[1]) * (la[1] - lb[1]) + (la[2] - lb[2]) * (la[2] - lb[2]));
            sum += d[i];
        }
        Arrays.sort(d);
        return new double[]{sum / d.length, d[Math.min(d.length - 1, (int) (d.length * 0.99))], d[d.length - 1]};
    }

    private static void lab(int argb, float[] out) {
        Oklab.fromLinear(Oklab.toLinear(argb >> 16), Oklab.toLinear(argb >> 8), Oklab.toLinear(argb), out);
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
