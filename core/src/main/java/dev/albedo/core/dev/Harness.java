package dev.albedo.core.dev;

import dev.albedo.core.Albedo;
import dev.albedo.core.Keys;
import dev.albedo.core.Platform;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyGenerator;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyTurn;
import dev.albedo.core.ui.SettingsEditor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

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

        /** Writes the next rendered frame to {@code png}, then calls {@link HarnessLog#log}. */
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
         * @param view the sky directions of the corners of a camera view ({@code HarnessImages.viewCorners}), or null for
         *        the equirectangular image
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

    /** One harness command, which gets the text after its verb. */
    private interface Verb {
        void run(Harness harness, String arg);
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

    /** The smallest mean difference of a channel in 8-bit steps at which {@code framechanged} says that the frame changed. */
    static final double FRAME_CHANGE_LIMIT = 8.0;
    private static final Keys.Key LEFT_CONTROL = Keys.byName("LEFT_CONTROL"), RIGHT_CONTROL = Keys.byName("RIGHT_CONTROL");

    /** The commands by verb; a toggle key that is not a verb sets that toggle of the active profile. */
    private static final Map<String, Verb> VERBS = verbs();

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
        HarnessLog.open(dir);
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
        if (burstLeft > 0) return;
        while (!queue.isEmpty() && waitTicks == 0 && !waitingForWorld && burstLeft == 0) {
            String line = queue.poll().trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            HarnessLog.log("> " + line);
            try {
                execute(line);
            } catch (RuntimeException e) {
                HarnessLog.fail(e.toString());
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
            HarnessLog.log("realclick at " + at[0] + "," + at[1]);
        } else {
            HarnessLog.fail("realclick is not available on this version");
        }
    }

    /** Runs one command line. */
    private void execute(String line) {
        int space = line.indexOf(' ');
        String name = space < 0 ? line : line.substring(0, space);
        String arg = space < 0 ? "" : line.substring(space + 1).trim();
        Verb verb = VERBS.get(name);
        Toggle toggle = verb == null ? Toggle.byKey(name) : null;
        if (toggle != null) verb = profileVerb((profile, on) -> profile.with(toggle, on.equals("on")));
        if (verb == null) HarnessLog.fail("unknown command: " + name);
        else verb.run(this, arg);
    }

    private static Map<String, Verb> verbs() {
        Map<String, Verb> v = new HashMap<String, Verb>();
        v.put("world", (h, arg) -> h.host.openWorld(arg));
        v.put("waitworld", (h, arg) -> {
            h.waitingForWorld = true;
        });
        v.put("run", (h, arg) -> h.host.runCommand(arg));
        v.put("wait", (h, arg) -> {
            h.waitTicks = Integer.parseInt(arg);
        });
        v.put("mod", configVerb((config, arg) -> config.withEnabled(arg.equals("on"))));
        v.put("profile", configVerb((config, arg) -> config.withActiveProfile(arg).withEnabled(true)));
        v.put("profilejson", configVerb(HarnessConfigCommands::withProfileJson));
        v.put("effects", profileVerb((profile, arg) -> HarnessConfigCommands.withEffects(profile, arg.split("\\s+"))));
        v.put("look", profileVerb((profile, arg) -> HarnessConfigCommands.withLook(profile, arg.split("\\s+"))));
        v.put("sky", profileVerb(HarnessConfigCommands::withSky));
        v.put("skyadd", profileVerb(HarnessConfigCommands::withSkyLayer));
        v.put("skyrender", profileVerb(HarnessConfigCommands::withSkyRender));
        v.put("skyturn", profileVerb((profile, arg) -> HarnessConfigCommands.withSkyTurning(profile, arg.split("\\s+"))));
        v.put("skycompare", (h, arg) -> h.skyCompare(arg.isEmpty() ? SKY_TRIES : Integer.parseInt(arg)));
        v.put("skytime", (h, arg) -> h.skyTime(arg.isEmpty() ? new String[0] : arg.split("\\s+")));
        v.put("skyclock", (h, arg) -> skyClock(arg));
        v.put("frames", (h, arg) -> h.frames(arg.split("\\s+")));
        v.put("chromagpu", (h, arg) -> Albedo.get().chromaGpu(arg.equals("on")));
        v.put("chromatime", (h, arg) -> h.chromaTime(arg.isEmpty() ? CHROMA_FRAMES : Integer.parseInt(arg)));
        v.put("hooks", (h, arg) -> HarnessLog.log(hooksLine(h.host.hookCounters())));
        v.put("cursorfree", (h, arg) -> HarnessLog.log(CursorClip.check(h.dir)));
        v.put("fov", (h, arg) -> h.host.setFov(Integer.parseInt(arg)));
        v.put("clouds", (h, arg) -> HarnessLog.log(cloudsLine(h.host.cloudsDrawn(), arg)));
        v.put("keepframe", (h, arg) -> h.keepFrame());
        v.put("framechanged", Harness::frameChanged);
        v.put("settings", Harness::settings);
        v.put("ui", (h, arg) -> SettingsEditor.script(arg));
        v.put("type", Harness::type);
        v.put("key", (h, arg) -> {
            h.key(arg, true);
            h.key(arg, false);
        });
        v.put("keydown", (h, arg) -> h.key(arg, true));
        v.put("keyup", (h, arg) -> h.key(arg, false));
        v.put("expect", Harness::expectScreen);
        v.put("hud", (h, arg) -> h.host.setHudVisible(arg.equals("on")));
        v.put("reload", (h, arg) -> h.platform.reloadResources());
        v.put("screenshot", (h, arg) -> h.host.screenshot(new File(h.dir, arg.endsWith(".png") ? arg : arg + ".png")));
        v.put("quit", (h, arg) -> h.host.quit());
        return Collections.unmodifiableMap(v);
    }

    /** A command that applies a change of the stored config. */
    private static Verb configVerb(BiFunction<Config, String, Config> change) {
        return (h, arg) -> {
            Albedo albedo = Albedo.get();
            albedo.apply(change.apply(albedo.storedConfig(), arg));
        };
    }

    /** A command that applies a change of the active profile. */
    private static Verb profileVerb(BiFunction<Profile, String, Profile> change) {
        return configVerb((config, arg) -> config.withProfile(change.apply(config.activeProfile(), arg)));
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
        return now.equals(expected) ? "clouds " + now : HarnessLog.FAIL_PREFIX + "clouds " + now + ", expected " + expected;
    }

    private void settings(String arg) {
        if (arg.equals("open")) platform.openSettings();
        else host.closeScreen();
    }

    private void keepFrame() {
        host.framePixels((w, h, argb) -> keptFrame = HarnessImages.middle(argb, w, h));
    }

    /** Logs whether the middle of the next frame differs from the frame of the last {@code keepframe}, for example after a block goes. */
    private void frameChanged(String name) {
        int[] kept = keptFrame;
        if (kept == null) {
            HarnessLog.fail("framechanged " + name + ": no keepframe before it");
            return;
        }
        host.framePixels((w, h, argb) -> {
            double mean = HarnessImages.frameDifference(kept, HarnessImages.middle(argb, w, h), HarnessImages.FAR_STEP)[0];
            HarnessLog.log(frameChangedLine(name, mean));
        });
    }

    /** The log line of {@code framechanged}, which starts with "!" when the mean difference is not above its limit. */
    static String frameChangedLine(String name, double mean) {
        boolean changed = mean > FRAME_CHANGE_LIMIT;
        return String.format(Locale.ROOT, "%sframechanged %s: mean %.1f steps (limit %.1f)%s", changed ? "" : HarnessLog.FAIL_PREFIX, name,
                mean, FRAME_CHANGE_LIMIT, changed ? "" : ": the frame did not change");
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
        if (dropped > 0) HarnessLog.fail("the window dropped " + dropped + " of " + text.length() + " typed characters");
    }

    /** Presses or releases a key by its config name, where a key that goes down stays held until it goes up. */
    private void key(String name, boolean down) {
        Keys.Key key = Keys.byName(name);
        if (key == null) throw new IllegalArgumentException("unknown key: " + name);
        if (down) held.add(key);
        else held.remove(key);
        if (!host.key(key, down, ctrlHeld())) HarnessLog.fail("key input is not available on this version");
    }

    private boolean ctrlHeld() {
        return held.contains(LEFT_CONTROL) || held.contains(RIGHT_CONTROL);
    }

    /** Logs PASS when the settings screen is {@code open} or {@code closed} as the argument says, else a line that starts with "!". */
    private void expectScreen(String state) {
        if (!state.equals("open") && !state.equals("closed")) throw new IllegalArgumentException("expect open|closed");
        boolean open = host.settingsOpen();
        if (open == state.equals("open")) HarnessLog.log("PASS the settings screen is " + state);
        else HarnessLog.fail("the settings screen is " + (open ? "open" : "closed") + ", expected " + state);
    }

    /**
     * Bakes the current sky design on the CPU, draws the same program with the sharp sky shader, and logs how far apart
     * the two images are; while the shader compiles, it runs again on the next tick.
     */
    private void skyCompare(int tries) {
        SkyImage world = Albedo.skyImage();
        if (world == null || !world.program().drawsSharp()) {
            HarnessLog.fail("skycompare: the sky is not a custom sky that the shader can draw");
            return;
        }
        if (compareCpu == null || !compareCpu.design().equals(world.design())) compareCpu = SkyGenerator.bake(world.design(), COMPARE_W, COMPARE_H);
        SkyImage cpu = compareCpu;
        boolean started = host.drawSharpSky(cpu, cpu.program().block(), COMPARE_W, COMPARE_H, 1, null, new File(dir, "sky-gpu.png"), (gpu, nanos) -> {
            double[] d = HarnessImages.oklabDifference(cpu.argb(), gpu);
            HarnessLog.log(String.format(Locale.ROOT, "skycompare %dx%d: mean %.4f, 99%% %.4f, max %.4f: %s", COMPARE_W, COMPARE_H,
                    d[0], d[1], d[2], d[0] < COMPARE_LIMIT ? "same" : "DIFFERENT"));
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
            HarnessLog.fail("skytime: the sky is not a custom sky that the shader can draw");
            return;
        }
        int given = args.length >= 5 ? 5 : args.length >= 2 ? 2 : 0;
        int width = given > 0 ? Integer.parseInt(args[0]) : TIME_W, height = given > 0 ? Integer.parseInt(args[1]) : TIME_H;
        float yaw = given == 5 ? Float.parseFloat(args[2]) : TIME_YAW, pitch = given == 5 ? Float.parseFloat(args[3]) : TIME_PITCH;
        float fov = given == 5 ? Float.parseFloat(args[4]) : TIME_FOV;
        int tries = args.length > given ? Integer.parseInt(args[given]) : SKY_TRIES;
        float[] view = HarnessImages.viewCorners(yaw, pitch, fov, (float) width / height);
        float[] block = world.program().at(SkyClock.longSeconds(), SkyTurn.STILL);
        boolean started = host.drawSharpSky(world, block, width, height, TIME_REPEAT, view, null, (gpu, nanos) -> {
            if (nanos < 0) HarnessLog.fail("skytime: the GPU gave no time");
            else HarnessLog.log(String.format(Locale.ROOT, "skytime %dx%d: %.3f ms on the GPU for each draw", width, height, nanos / 1e6));
        });
        if (started) return;
        StringBuilder line = new StringBuilder("skytime");
        for (int i = 0; i < given; i++) line.append(' ').append(args[i]);
        again(line.append(' ').append(tries - 1).toString(), tries);
    }

    private void chromaTime(int frames) {
        if (!host.timeChroma(frames)) HarnessLog.fail("chromatime: this version has no GPU pass of Chroma, or the pass does not run");
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
        HarnessLog.log(String.format(Locale.ROOT, "chromatime: %d frames, GPU p50 %.3f ms, p95 %.3f ms, max %.3f ms (start p50 %.3f ms,"
                        + " end p50 %.3f ms), CPU p50 %.3f ms, p95 %.3f ms, GPU memory %.1f MiB for %s",
                gpu.length, ms(gpu, 50), ms(gpu, 95), ms(gpu, 100), ms(gpuStart, 50), ms(gpuEnd, 50), ms(cpu, 50), ms(cpu, 95),
                bytes / 1048576.0, atlases));
    }

    private static double ms(long[] nanos, int p) {
        return HarnessImages.percentileMs(nanos, p);
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
        HarnessLog.log(String.format(Locale.ROOT, "frame %s at %.5f s", name, seconds));
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
            HarnessLog.fail(line.split(" ")[0] + ": this version has no sharp sky shader, or it does not compile");
            return;
        }
        queue.addFirst(line);
        waitTicks = 1;
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
            HarnessLog.fail("cannot read commands: " + e);
        }
    }

    private void writeStatus(String status) {
        if (status.equals(lastStatus)) return;
        lastStatus = status;
        HarnessLog.write(new File(dir, "status.txt"), status, false);
    }
}
