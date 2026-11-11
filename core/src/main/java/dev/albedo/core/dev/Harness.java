package dev.albedo.core.dev;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.Keys;
import dev.albedo.core.Platform;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.sky.Oklab;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyGenerator;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyPresets;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyTurn;
import dev.albedo.core.ui.SettingsEditor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Development harness, created only with {@code -Dalbedo.harness=<directory>}, that runs the lines of
 * {@code <dir>/commands.txt} so screenshots can be taken on a hidden desktop with no keyboard, mouse or sound.
 */
public final class Harness {
    /** What the harness needs from the game; implemented next to each version's adapter. */
    public interface Host {
        boolean inWorld();

        /** Whether the game still shows its loading screen, where no command runs, because the models can be missing. */
        default boolean loading() { return false; }

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

        /**
         * Sends a press or a release of a key into the entry point of the game that the window events call, as a real
         * keyboard does, and returns false when this version cannot do that.
         *
         * @param ctrl whether a Control key is held, for the versions that read it from the event
         */
        boolean key(Keys.Key key, boolean down, boolean ctrl);

        /**
         * Sends one typed character into the entry point of the game that the window events call, and returns false when
         * the window would drop it, for example because the game did not start text input.
         *
         * @param afterKey whether the key of the character went down just before, else the window sends the character as
         *        typed text with no key, as for a character that needs Shift or AltGr
         */
        boolean character(char c, boolean afterKey);

        /** Whether the settings screen of Albedo is open. */
        boolean settingsOpen();

        void quit();

        /**
         * Mutes sound, disables the narrator, keeps the game running without focus and keeps the camera where the harness
         * aims it, and must be cheap because it runs every tick.
         */
        void quiet();

        /** Sets the field of view in degrees, in the range of the game option. */
        void setFov(int degrees);

        /**
         * Draws the program and the term images of a sky with the sharp sky shader {@code repeat} times into an image of
         * this size, and later gives its pixels and the GPU time of one draw to {@code done}.
         *
         * @param block the block of the program for the draw, from {@link SkyProgram#block} or {@link SkyProgram#at}
         * @param view the sky directions of the corners of a camera view ({@link #viewCorners}), or null for the equirectangular image
         * @param png a file for the image, or null
         * @return false while the shader compiles, or when this version or GPU has no sharp sky
         */
        boolean drawSharpSky(SkyImage sky, float[] block, int width, int height, int repeat, float[] view, File png, SkyDone done);

        /**
         * Measures the GPU and CPU time of the GPU pass of Chroma in each of the next frames, then logs the times, the
         * atlas sizes and the GPU memory of the pass.
         *
         * @return false when this version has no GPU pass of Chroma, or the pass does not run now
         */
        boolean timeChroma(int frames);

        /** Gives the pixels of the last frame that the game drew to {@code done}, on any thread. */
        void framePixels(FramePixels done);

        /** The counters of the hooks of this version that can miss their target with no error. */
        List<HookCounter> hookCounters();

        /** Whether the next frame draws clouds with visible pixels, from the state that the renderer reads. */
        boolean cloudsDrawn();
    }

    /** Receives the pixels of {@link Host#framePixels}. */
    public interface FramePixels {
        /** @param argb the ARGB pixels in rows from the top */
        void accept(int width, int height, int[] argb);
    }

    /** Receives the image of {@link Host#drawSharpSky}. */
    public interface SkyDone {
        /** @param gpuNanos the GPU time of one draw in nanoseconds, or -1 when the GPU did not give it */
        void accept(int[] argb, long gpuNanos);
    }

    /** The size of the image that {@code skycompare} draws on the GPU and bakes on the CPU. */
    private static final int COMPARE_W = 512, COMPARE_H = 256;
    /** The size and the count of the draws that {@code skytime} measures when the command gives no size. */
    private static final int TIME_W = 1600, TIME_H = 900, TIME_REPEAT = 20;
    /** The camera of {@code skytime} when the command gives none: the yaw, the pitch and the vertical field of view in degrees. */
    private static final float TIME_YAW = 180, TIME_PITCH = -20, TIME_FOV = 70;
    /** The ticks that a sky command waits for the shader to compile. */
    private static final int SKY_TRIES = 200;
    /** The largest mean OKLab distance between the GPU and the CPU sky that {@code skycompare} accepts. */
    static final double COMPARE_LIMIT = 0.02;
    /** The number of frames that {@code chromatime} measures. */
    private static final int CHROMA_FRAMES = 300;
    /** The ticks that {@code chromacompare} waits for frames at the time 0, and for the still colors that the CPU paints. */
    private static final int COMPARE_GPU_TICKS = 10, COMPARE_CPU_TICKS = 60;
    /** The ticks that {@code chromacompare} waits for the pixels of the two frames before it stops. */
    private static final int COMPARE_PIXEL_TICKS = 200;
    /** A pixel is far off when a channel differs by more 8-bit steps than this, which dither and rounding never do. */
    static final int CHROMA_FAR_STEP = 48;
    /** The largest mean difference of a channel in 8-bit steps that {@code chromacompare} accepts. */
    static final double CHROMA_MEAN_LIMIT = 1.0;
    /** The largest part of far-off pixels that {@code chromacompare} accepts. */
    static final double CHROMA_FAR_LIMIT = 0.002;

    /** The smallest mean difference of a channel in 8-bit steps at which {@code framechanged} says that the frame changed. */
    static final double FRAME_CHANGE_LIMIT = 8.0;

    private static volatile File logFile;

    private final File dir;
    private final Platform platform;
    private final Host host;
    private final Deque<String> queue = new ArrayDeque<String>();
    private int waitTicks;
    private boolean waitingForWorld;
    private String lastStatus = "";
    /** The GUI position of a {@code ui realclick} whose button is down until the next tick, or null. */
    private int[] pressedAt;
    /** The CPU bake of the last {@code skycompare}, which its tries share. */
    private SkyImage compareCpu;
    /** The screenshots that the running {@code frames} command still takes, its name and the index of the next one. */
    private int burstLeft;
    private String burstName;
    private int burstIndex;
    /** The keys that {@code keydown} holds until {@code keyup}. */
    private final Set<Keys.Key> held = new HashSet<Keys.Key>();
    /** The step of the running {@code chromacompare}, 0 when none runs, and the ticks that its last step waited for pixels. */
    private int chromaStep;
    private int chromaTicks;
    /** The pixels of the frames of {@code chromacompare} with the GPU colors and with the CPU colors, which other threads give. */
    private volatile int[] chromaGpuFrame, chromaCpuFrame;
    /** The width and the height of the frame with the GPU colors. */
    private volatile int[] chromaFrameSize;
    /** The middle of the frame of the last {@code keepframe}, which another thread gives. */
    private volatile int[] keptFrame;

    private Harness(File dir, Platform platform) {
        this.dir = dir;
        this.platform = platform;
        this.host = platform.harnessHost();
    }

    /** Whether the game runs with the harness, where it must never grab the mouse, because a grab clips the cursor of the whole desktop. */
    public static boolean active() {
        return !System.getProperty("albedo.harness", "").trim().isEmpty();
    }

    /** Counts one skipped window call and returns true when the game runs with the harness, otherwise returns false. */
    public static boolean skip(HookCounter counter) {
        if (!active()) return false;
        counter.run();
        return true;
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
        if (host.loading()) return;
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
        if (chromaStep > 0) {
            chromaCompareStep();
            return;
        }
        while (!queue.isEmpty() && waitTicks == 0 && !waitingForWorld && burstLeft == 0 && chromaStep == 0) {
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
        else if (verb.equals("effects")) sc.apply(config.withProfile(withEffects(config.activeProfile(), arg.split("\\s+"))));
        else if (verb.equals("sky")) sc.apply(config.withProfile(withSky(config.activeProfile(), arg)));
        else if (verb.equals("skyadd")) sc.apply(config.withProfile(withSkyLayer(config.activeProfile(), arg)));
        else if (verb.equals("skyrender")) sc.apply(config.withProfile(withSkyRender(config.activeProfile(), arg)));
        else if (verb.equals("skyturn")) sc.apply(config.withProfile(withSkyTurning(config.activeProfile(), arg.split("\\s+"))));
        else if (verb.equals("skycompare")) skyCompare(arg.isEmpty() ? SKY_TRIES : Integer.parseInt(arg));
        else if (verb.equals("skytime")) skyTime(arg.isEmpty() ? new String[0] : arg.split("\\s+"));
        else if (verb.equals("skyclock")) skyClock(arg);
        else if (verb.equals("frames")) frames(arg.split("\\s+"));
        else if (verb.equals("chromagpu")) sc.chromaGpu(arg.equals("on"));
        else if (verb.equals("chromatime")) chromaTime(arg.isEmpty() ? CHROMA_FRAMES : Integer.parseInt(arg));
        else if (verb.equals("chromacompare")) chromaCompare(sc);
        else if (verb.equals("hooks")) log(hooksLine(host.hookCounters()));
        else if (verb.equals("cursorfree")) log(CursorClip.check(dir));
        else if (verb.equals("fov")) host.setFov(Integer.parseInt(arg));
        else if (verb.equals("clouds")) log(cloudsLine(host.cloudsDrawn(), arg));
        else if (verb.equals("keepframe")) host.framePixels((w, h, argb) -> keptFrame = middle(argb, w, h));
        else if (verb.equals("framechanged")) frameChanged(arg);
        else if (verb.equals("settings")) {
            if (arg.equals("open")) platform.openSettings();
            else host.closeScreen();
        } else if (verb.equals("ui")) SettingsEditor.script(arg);
        else if (verb.equals("type")) type(arg);
        else if (verb.equals("key")) {
            key(arg, true);
            key(arg, false);
        } else if (verb.equals("keydown")) key(arg, true);
        else if (verb.equals("keyup")) key(arg, false);
        else if (verb.equals("expect")) expectScreen(arg);
        else if (verb.equals("hud")) host.setHudVisible(arg.equals("on"));
        else if (verb.equals("reload")) platform.reloadResources();
        else if (verb.equals("screenshot")) host.screenshot(new File(dir, arg.endsWith(".png") ? arg : arg + ".png"));
        else if (verb.equals("quit")) host.quit();
        else log("! unknown command: " + verb);
    }

    /** The log line of the command "hooks", with the runs of each counter after its name. */
    static String hooksLine(List<HookCounter> counters) {
        StringBuilder line = new StringBuilder("hooks:");
        for (HookCounter counter : counters) line.append(' ').append(counter.name()).append('=').append(counter.runs());
        return line.toString();
    }

    /**
     * The log line of {@code clouds shown|hidden}, which starts with "!" when the game does not draw the clouds as the
     * command expects.
     */
    static String cloudsLine(boolean drawn, String expected) {
        if (!expected.equals("shown") && !expected.equals("hidden")) throw new IllegalArgumentException("clouds shown|hidden");
        String now = drawn ? "shown" : "hidden";
        return now.equals(expected) ? "clouds " + now : "! clouds " + now + ", expected " + expected;
    }

    /** Logs whether the middle of the next frame differs from the frame of the last {@code keepframe}, for example after a block goes. */
    private void frameChanged(String name) {
        int[] kept = keptFrame;
        if (kept == null) {
            log("! framechanged " + name + ": no keepframe before it");
            return;
        }
        host.framePixels((w, h, argb) -> log(frameChangedLine(name, chromaDifference(kept, middle(argb, w, h), CHROMA_FAR_STEP)[0])));
    }

    /** The middle part of a frame, a fifth of its width and of its height, where a check puts the thing that it looks at. */
    static int[] middle(int[] argb, int width, int height) {
        int w = Math.max(1, width / 5), h = Math.max(1, height / 5), x0 = (width - w) / 2, y0 = (height - h) / 2;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) System.arraycopy(argb, (y0 + y) * width + x0, out, y * w, w);
        return out;
    }

    /** The log line of {@code framechanged}, which starts with "!" when the mean difference is not above its limit. */
    static String frameChangedLine(String name, double mean) {
        boolean changed = mean > FRAME_CHANGE_LIMIT;
        return String.format(Locale.ROOT, "%sframechanged %s: mean %.1f steps (limit %.1f)%s", changed ? "" : "! ", name, mean,
                FRAME_CHANGE_LIMIT, changed ? "" : ": the frame did not change");
    }

    /** Types as a real keyboard does: the key of a letter or digit goes down, its character follows, and the key goes up. */
    private void type(String text) {
        int dropped = 0;
        for (char c : text.toCharArray()) {
            Keys.Key key = c < 128 && Character.isLetterOrDigit(c) ? Keys.byName(String.valueOf(c)) : null;
            if (key != null) host.key(key, true, ctrlHeld());
            if (!host.character(c, key != null)) dropped++;
            if (key != null) host.key(key, false, ctrlHeld());
        }
        if (dropped > 0) log("! the window dropped " + dropped + " of " + text.length() + " typed characters");
    }

    /** Presses or releases a key by its config name, where a key that goes down stays held until it goes up. */
    private void key(String name, boolean down) {
        Keys.Key key = Keys.byName(name);
        if (key == null) throw new IllegalArgumentException("unknown key: " + name);
        if (down) held.add(key);
        else held.remove(key);
        if (!host.key(key, down, ctrlHeld())) log("! key input is not available on this version");
    }

    private boolean ctrlHeld() {
        return held.contains(Keys.byName("LEFT_CONTROL")) || held.contains(Keys.byName("RIGHT_CONTROL"));
    }

    /** Logs PASS when the settings screen is {@code open} or {@code closed} as the argument says, else a line that starts with "!". */
    private void expectScreen(String state) {
        if (!state.equals("open") && !state.equals("closed")) throw new IllegalArgumentException("expect open|closed");
        boolean open = host.settingsOpen();
        if (open == state.equals("open")) log("PASS the settings screen is " + state);
        else log("! the settings screen is " + (open ? "open" : "closed") + ", expected " + state);
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
            // The config accepts only percents, and a speed above 100 has no place in the turns of the GPU pass.
            if (value < 0 || value > 100) throw new IllegalArgumentException(args[i] + " must be from 0 to 100: " + value);
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
     * back to the vanilla sky with {@code sky off}, where a preset with the default turning stands still until {@code skyturn}.
     */
    static Profile withSky(Profile profile, String arg) {
        CategorySettings sky = profile.get(Category.SKY);
        if (arg.equals("off")) return profile.with(Category.SKY, sky.withAppearance(Appearance.ORIGINAL));
        String[] parts = arg.trim().split("\\s+");
        SkyDesign design = SkyPresets.byName(parts[0]);
        if (design == null || parts.length > 2) {
            throw new IllegalArgumentException("sky <" + String.join("|", SkyPresets.all().keySet()) + "|off> [layers]");
        }
        // A still sky gives the same screenshots in each run, and Trails keeps the turning that draws its trails.
        if (design.turning().equals(SkyTurning.DEFAULT)) design = design.withTurning(SkyTurning.STILL);
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

    /**
     * Sets the turning of the custom sky from {@code <percent> [height N]}, where a missing option keeps its value in
     * {@link SkyTurning#STILL} ({@code skyturn 100 height 75}).
     */
    static Profile withSkyTurning(Profile profile, String[] args) {
        SkyTurning t = SkyTurning.STILL.withAmount(Integer.parseInt(args[0]));
        for (int i = 1; i < args.length; i++) {
            if (i + 1 >= args.length) throw new IllegalArgumentException("skyturn <percent> [height N]");
            if (args[i].equals("height")) t = t.withPoleHeight(Integer.parseInt(args[++i]));
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
        boolean started = host.drawSharpSky(cpu, cpu.program().block(), COMPARE_W, COMPARE_H, 1, null, new File(dir, "sky-gpu.png"), (gpu, nanos) -> {
            double[] d = difference(cpu.argb(), gpu);
            log(String.format(Locale.ROOT, "skycompare %dx%d: mean %.4f, 99%% %.4f, max %.4f: %s", COMPARE_W, COMPARE_H, d[0], d[1], d[2],
                    d[0] < COMPARE_LIMIT ? "same" : "DIFFERENT"));
        });
        if (!started) again("skycompare " + (tries - 1), tries);
    }

    /**
     * Draws the world sky with the sharp sky shader into a camera view full of sky and logs the GPU time of one draw, from
     * {@code skytime [width height [yaw pitch fov]] [tries]}.
     */
    private void skyTime(String[] args) {
        SkyImage world = Albedo.skyImage();
        if (world == null || !world.program().drawsSharp()) {
            log("! skytime: the sky is not a custom sky that the shader can draw");
            return;
        }
        int given = args.length >= 5 ? 5 : args.length >= 2 ? 2 : 0;
        int width = given > 0 ? Integer.parseInt(args[0]) : TIME_W, height = given > 0 ? Integer.parseInt(args[1]) : TIME_H;
        float yaw = given == 5 ? Float.parseFloat(args[2]) : TIME_YAW, pitch = given == 5 ? Float.parseFloat(args[3]) : TIME_PITCH;
        float fov = given == 5 ? Float.parseFloat(args[4]) : TIME_FOV;
        int tries = args.length > given ? Integer.parseInt(args[given]) : SKY_TRIES;
        float[] view = viewCorners(yaw, pitch, fov, (float) width / height);
        float[] block = world.program().at(SkyClock.longSeconds(), SkyTurn.STILL);
        boolean started = host.drawSharpSky(world, block, width, height, TIME_REPEAT, view, null, (gpu, nanos) ->
                log(nanos < 0 ? "! skytime: the GPU gave no time"
                        : String.format(Locale.ROOT, "skytime %dx%d: %.3f ms on the GPU for each draw", width, height, nanos / 1e6)));
        if (started) return;
        StringBuilder line = new StringBuilder("skytime");
        for (int i = 0; i < given; i++) line.append(' ').append(args[i]);
        again(line.append(' ').append(tries - 1).toString(), tries);
    }

    /**
     * The sky directions of the corners of a camera view, top left, bottom left, bottom right and top right, as x, y and z
     * each, on the plane one unit in front of the camera.
     *
     * @param yaw the Minecraft yaw in degrees, where 0 looks south and 90 looks west
     * @param pitch the Minecraft pitch in degrees, where a negative pitch looks up
     * @param fov the vertical field of view in degrees
     * @param aspect the width over the height
     */
    static float[] viewCorners(float yaw, float pitch, float fov, float aspect) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double[] forward = {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
        double[] right = {-Math.cos(y), 0, -Math.sin(y)};
        double[] up = {right[1] * forward[2] - right[2] * forward[1], right[2] * forward[0] - right[0] * forward[2],
            right[0] * forward[1] - right[1] * forward[0]};
        double ty = Math.tan(Math.toRadians(fov) / 2), tx = ty * aspect;
        double[][] corners = {{-1, 1}, {-1, -1}, {1, -1}, {1, 1}};
        float[] out = new float[12];
        for (int c = 0; c < 4; c++) {
            for (int k = 0; k < 3; k++) out[c * 3 + k] = (float) (forward[k] + corners[c][0] * tx * right[k] + corners[c][1] * ty * up[k]);
        }
        return out;
    }

    /** Asks the adapter to measure the GPU pass of Chroma in the next frames, and logs when this version cannot. */
    private void chromaTime(int frames) {
        if (!host.timeChroma(frames)) log("! chromatime: this version has no GPU pass of Chroma, or the pass does not run");
    }

    /**
     * Starts {@code chromacompare}: it holds the clock at the time 0, takes a frame with the GPU colors, stops the pass,
     * takes a frame with the still colors that the CPU paints, starts the pass again, lets the clock run and logs how far
     * apart the two frames are.
     */
    private void chromaCompare(Albedo sc) {
        if (sc.chromaStill()) {
            log("! chromacompare: the GPU pass of Chroma does not run");
            return;
        }
        SkyClock.hold(0);
        chromaGpuFrame = null;
        chromaCpuFrame = null;
        chromaFrameSize = null;
        chromaStep = 1;
        waitTicks = COMPARE_GPU_TICKS;
    }

    /** Does the next step of {@code chromacompare}, which holds the next commands until it logs its result. */
    private void chromaCompareStep() {
        Albedo sc = Albedo.get();
        if (chromaStep == 1) {
            host.framePixels((w, h, argb) -> {
                chromaFrameSize = new int[]{w, h};
                chromaGpuFrame = argb;
            });
            sc.chromaGpu(false);
            chromaStep = 2;
            waitTicks = COMPARE_CPU_TICKS;
        } else if (chromaStep == 2) {
            host.framePixels((w, h, argb) -> chromaCpuFrame = argb);
            sc.chromaGpu(true);
            SkyClock.run();
            chromaStep = 3;
            chromaTicks = 0;
        } else if (chromaGpuFrame != null && chromaCpuFrame != null) {
            int[] size = chromaFrameSize;
            log(chromaCompareLine(size[0], size[1], chromaDifference(chromaGpuFrame, chromaCpuFrame, CHROMA_FAR_STEP)));
            chromaStep = 0;
        } else if (++chromaTicks > COMPARE_PIXEL_TICKS) {
            log("! chromacompare: the game gave no frame");
            chromaStep = 0;
        }
    }

    /**
     * The mean difference of the color channels of two frames of the same size in 8-bit steps, and the part of the pixels
     * that have a channel more than {@code farStep} steps off.
     *
     * @return {mean, far-off part}, or {Infinity, 1} when the frames have different sizes
     */
    static double[] chromaDifference(int[] a, int[] b, int farStep) {
        if (a.length != b.length || a.length == 0) return new double[]{Double.POSITIVE_INFINITY, 1};
        long sum = 0;
        int far = 0;
        for (int i = 0; i < a.length; i++) {
            int largest = 0;
            for (int shift = 0; shift <= 16; shift += 8) {
                int d = Math.abs(((a[i] >> shift) & 0xFF) - ((b[i] >> shift) & 0xFF));
                sum += d;
                largest = Math.max(largest, d);
            }
            if (largest > farStep) far++;
        }
        return new double[]{sum / (3.0 * a.length), far / (double) a.length};
    }

    /** The log line of {@code chromacompare}, which starts with "!" when the mean or the part of far-off pixels is above its limit. */
    static String chromaCompareLine(int width, int height, double[] d) {
        boolean same = d[0] <= CHROMA_MEAN_LIMIT && d[1] <= CHROMA_FAR_LIMIT;
        return String.format(Locale.ROOT, "%schromacompare %dx%d: GPU at the time 0 against the CPU still colors: mean %.2f steps"
                        + " (limit %.1f), far-off pixels %.3f%% (limit %.1f%%): %s", same ? "" : "! ", width, height, d[0],
                CHROMA_MEAN_LIMIT, d[1] * 100, CHROMA_FAR_LIMIT * 100, same ? "same" : "DIFFERENT");
    }

    /**
     * Logs the times of the GPU pass of Chroma that an adapter measured for {@code chromatime}.
     *
     * @param frames for each frame in nanoseconds: the CPU time of the pass, the GPU time of the copy and the draws at the
     *     start of the frame, and the GPU time of the copy back at its end
     * @param bytes the GPU memory of the pass
     * @param atlases the names of the atlases that the pass turns
     */
    public static void logChromaTimes(List<long[]> frames, long bytes, String atlases) {
        int n = frames.size();
        long[] cpu = new long[n], gpuStart = new long[n], gpuEnd = new long[n], gpu = new long[n];
        for (int i = 0; i < n; i++) {
            long[] f = frames.get(i);
            cpu[i] = f[0];
            gpuStart[i] = f[1];
            gpuEnd[i] = f[2];
            gpu[i] = f[1] + f[2];
        }
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
        int count = args.length == 2 ? Integer.parseInt(args[0]) : 0;
        // A count below 1 would stop the queue for good, because only a screenshot of the burst counts it down.
        if (count < 1) throw new IllegalArgumentException("frames <count of 1 or more> <name>");
        burstLeft = count;
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

    /** Holds the clock of the moving sky layers still at a time in seconds ({@code skyclock 12.5}), or lets it run ({@code skyclock run}). */
    private static void skyClock(String arg) {
        if (arg.equals("run")) SkyClock.run();
        else SkyClock.hold(Float.parseFloat(arg));
    }

    /** Runs a sky command line again on the next tick, until no tries are left. */
    private void again(String line, int tries) {
        if (tries <= 0) {
            log("! " + line.split(" ")[0] + ": this version has no sharp sky shader, or it does not compile");
            return;
        }
        queue.addFirst(line);
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

    private void takeCommandFile() {
        File commands = new File(dir, "commands.txt");
        if (!commands.isFile()) return;
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
