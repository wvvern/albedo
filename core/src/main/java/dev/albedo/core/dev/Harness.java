package dev.albedo.core.dev;

import dev.albedo.core.Albedo;
import dev.albedo.core.HarnessHost;
import dev.albedo.core.Keys;
import dev.albedo.core.Platform;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.dev.HarnessQueue.Command;
import dev.albedo.core.dev.HarnessQueue.Outcome;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.ui.ScriptPort;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;

/**
 * Development harness, created only with {@code -Dalbedo.harness=<directory>}, that runs the lines of
 * {@code <dir>/commands.txt} so screenshots can be taken on a hidden desktop with no keyboard, mouse or sound.
 */
public final class Harness {
    /** Receives the pixels of {@link HarnessHost#framePixels}. */
    public interface FramePixels {
        /** @param argb the ARGB pixels in rows from the top */
        void accept(int width, int height, int[] argb);
    }

    /** The draws of {@link HarnessHost#drawSharpSky}: the program and the term images of a sky, {@code repeat} times into an image of this size. */
    public static final class SkyDraw {
        public final SkyImage sky;
        /** The block of the program for the draw, from {@link SkyProgram#block} or {@link SkyProgram#at}. */
        public final float[] block;
        public final int width;
        public final int height;
        public final int repeat;
        /** The sky directions of the corners of a camera view ({@code HarnessImages.viewCorners}), or null for the equirectangular image. */
        public final float[] view;
        /** A file for the image, or null. */
        public final File png;

        public SkyDraw(SkyImage sky, float[] block, int width, int height, int repeat, float[] view, File png) {
            this.sky = sky;
            this.block = block;
            this.width = width;
            this.height = height;
            this.repeat = repeat;
            this.view = view;
            this.png = png;
        }
    }

    /** Receives the image of {@link HarnessHost#drawSharpSky}. */
    public interface SkyDone {
        /** @param gpuNanos the GPU time of one draw in nanoseconds, or -1 when the GPU did not give it */
        void accept(int[] argb, long gpuNanos);
    }

    /** One harness command. */
    private interface Verb {
        Outcome run(Harness harness, Command command);
    }

    /** A harness command that is done when it returns, which gets the text after its verb. */
    private interface Action {
        void run(Harness harness, String arg);
    }

    /** The number of frames that {@code chromatime} measures. */
    private static final int CHROMA_FRAMES = 300;

    /** The smallest mean difference of a channel in 8-bit steps at which {@code framechanged} says that the frame changed. */
    static final double FRAME_CHANGE_LIMIT = 8.0;
    private static final Keys.Key LEFT_CONTROL = Keys.byName("LEFT_CONTROL"), RIGHT_CONTROL = Keys.byName("RIGHT_CONTROL");

    /** The commands by verb; a toggle key that is not a verb sets that toggle of the active profile. */
    private static final Map<String, Verb> VERBS = verbs();

    private final File dir;
    private final Albedo albedo;
    private final Platform platform;
    private final HarnessHost host;
    private final HarnessQueue queue = new HarnessQueue();
    private final HarnessQueue.Runner runner;
    private final RealClick realClick = new RealClick();
    private final UiScript ui = new UiScript(realClick);
    private final HarnessSkyChecks sky;
    private String lastStatus = "";
    /** The keys that {@code keydown} holds until {@code keyup}. */
    private final Set<Keys.Key> held = new HashSet<Keys.Key>();
    /** The middle of the frame of the last {@code keepframe}, which another thread gives. */
    private volatile int[] keptFrame;

    private Harness(File dir, Albedo albedo) {
        this.dir = dir;
        this.albedo = albedo;
        this.platform = albedo.platform();
        this.host = platform.harnessHost();
        this.sky = new HarnessSkyChecks(host, dir);
        this.runner = new HarnessQueue.Runner() {
            @Override
            public boolean inWorld() {
                return host.inWorld();
            }

            @Override
            public Outcome run(Command command) {
                return execute(command);
            }
        };
    }

    /** The {@code ui} commands for the settings screen, and the {@code realclick} that they ask for. */
    private static final class UiScript implements ScriptPort {
        final Queue<String> commands = new ConcurrentLinkedQueue<String>();
        private final RealClick click;

        UiScript(RealClick click) {
            this.click = click;
        }

        @Override public boolean waiting() { return !commands.isEmpty(); }

        @Override public String poll() { return commands.poll(); }

        @Override public void log(String line) { HarnessLog.log(line); }

        @Override public void fail(String reason) { HarnessLog.fail(reason); }

        @Override public void realClick(int[] at) { click.request(at); }
    }

    /** The port through which the settings screen takes the {@code ui} commands. */
    public ScriptPort scriptPort() {
        return ui;
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
    public static Harness fromSystemProperty(Albedo albedo) {
        String path = System.getProperty("albedo.harness");
        if (path == null || path.trim().isEmpty()) return null;
        File dir = new File(path).getAbsoluteFile();
        dir.mkdirs();
        HarnessLog.open(dir);
        albedo.platform().info("Test harness enabled in " + dir);
        return new Harness(dir, albedo);
    }

    public void tick() {
        host.quiet();
        writeStatus(host.inWorld() ? "world" : "menu");
        takeCommandFile();
        if (host.loading()) return;
        realClick.tick(host);
        queue.advance(runner);
    }

    private Outcome execute(Command command) {
        Verb verb = VERBS.get(command.verb);
        Toggle toggle = verb == null ? Toggle.byKey(command.verb) : null;
        if (toggle != null) verb = done(profileVerb((profile, on) -> profile.with(toggle, on.equals("on"))));
        if (verb != null) return verb.run(this, command);
        HarnessLog.fail("unknown command: " + command.verb);
        return Outcome.DONE;
    }

    private static Map<String, Verb> verbs() {
        Map<String, Verb> v = new HashMap<String, Verb>();
        put(v, "world", (h, arg) -> h.host.openWorld(arg));
        put(v, "waitworld", (h, arg) -> h.queue.waitWorld());
        put(v, "run", (h, arg) -> h.host.runCommand(arg));
        put(v, "wait", (h, arg) -> h.queue.waitTicks(Integer.parseInt(arg)));
        put(v, "mod", configVerb((config, arg) -> config.withEnabled(arg.equals("on"))));
        put(v, "profile", configVerb((config, arg) -> config.withActiveProfile(arg).withEnabled(true)));
        put(v, "profilejson", configVerb(HarnessConfigCommands::withProfileJson));
        put(v, "effects", profileVerb((profile, arg) -> HarnessConfigCommands.withEffects(profile, arg.split("\\s+"))));
        put(v, "look", profileVerb((profile, arg) -> HarnessConfigCommands.withLook(profile, arg.split("\\s+"))));
        put(v, "sky", profileVerb(HarnessConfigCommands::withSky));
        put(v, "skyadd", profileVerb(HarnessConfigCommands::withSkyLayer));
        put(v, "skyrender", profileVerb(HarnessConfigCommands::withSkyRender));
        put(v, "skyturn", profileVerb((profile, arg) -> HarnessConfigCommands.withSkyTurning(profile, arg.split("\\s+"))));
        v.put("skycompare", (h, command) -> h.sky.compare(command));
        v.put("skytime", (h, command) -> h.sky.time(command));
        put(v, "skyclock", (h, arg) -> skyClock(arg));
        put(v, "frames", (h, arg) -> h.frames(arg.split("\\s+")));
        put(v, "chromagpu", (h, arg) -> h.albedo.chromaGpu(arg.equals("on")));
        put(v, "chromatime", (h, arg) -> h.chromaTime(arg.isEmpty() ? CHROMA_FRAMES : Integer.parseInt(arg)));
        put(v, "hooks", (h, arg) -> HarnessLog.log(hooksLine(h.host.hookCounters())));
        put(v, "cursorfree", (h, arg) -> HarnessLog.log(CursorClip.check(h.dir)));
        put(v, "fov", (h, arg) -> h.host.setFov(Integer.parseInt(arg)));
        put(v, "clouds", (h, arg) -> HarnessLog.log(cloudsLine(h.host.cloudsDrawn(), arg)));
        put(v, "keepframe", (h, arg) -> h.keepFrame());
        put(v, "framechanged", Harness::frameChanged);
        put(v, "settings", Harness::settings);
        put(v, "ui", (h, arg) -> h.ui.commands.add(arg));
        put(v, "type", Harness::type);
        put(v, "key", (h, arg) -> {
            h.key(arg, true);
            h.key(arg, false);
        });
        put(v, "keydown", (h, arg) -> h.key(arg, true));
        put(v, "keyup", (h, arg) -> h.key(arg, false));
        put(v, "expect", Harness::expectScreen);
        put(v, "hud", (h, arg) -> h.host.setHudVisible(arg.equals("on")));
        put(v, "reload", (h, arg) -> h.platform.reloadResources());
        put(v, "screenshot", (h, arg) -> h.host.screenshot(new File(h.dir, arg.endsWith(".png") ? arg : arg + ".png")));
        put(v, "quit", (h, arg) -> h.host.quit());
        return Collections.unmodifiableMap(v);
    }

    private static void put(Map<String, Verb> verbs, String name, Action action) {
        verbs.put(name, done(action));
    }

    private static Verb done(Action action) {
        return (h, command) -> {
            action.run(h, command.arg);
            return Outcome.DONE;
        };
    }

    /** A command that applies a change of the stored config. */
    private static Action configVerb(BiFunction<Config, String, Config> change) {
        return (h, arg) -> h.albedo.apply(change.apply(h.albedo.storedConfig(), arg));
    }

    /** A command that applies a change of the active profile. */
    private static Action profileVerb(BiFunction<Profile, String, Profile> change) {
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
        boolean given = args.length == 2;
        queue.burst(given ? Integer.parseInt(args[0]) : 0, given ? args[1] : null);
    }

    /**
     * Takes the next screenshot of a {@code frames} command; the adapter calls it on the render thread at the end of each
     * frame, after the frame is drawn.
     *
     * @param seconds the clock time that the Chroma colors of this frame have
     */
    public void frame(double seconds) {
        String name = queue.nextFrame();
        if (name == null) return;
        HarnessLog.log(String.format(Locale.ROOT, "frame %s at %.5f s", name, seconds));
        host.screenshot(new File(dir, name));
    }

    /** Holds the clock of the moving sky layers still at a time in seconds ({@code skyclock 12.5}), or lets it run ({@code skyclock run}). */
    private static void skyClock(String arg) {
        if (arg.equals("run")) SkyClock.run();
        else SkyClock.hold(Float.parseFloat(arg));
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
