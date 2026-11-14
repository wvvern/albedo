package dev.albedo.core.dev;

import dev.albedo.core.Albedo;
import dev.albedo.core.HarnessHost;
import dev.albedo.core.dev.HarnessQueue.Command;
import dev.albedo.core.dev.HarnessQueue.Outcome;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyGenerator;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyTurn;

import java.io.File;
import java.util.Locale;

/** The harness commands {@code skycompare} and {@code skytime}, which draw the sky with the sharp sky shader. */
final class HarnessSkyChecks {
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

    private final HarnessHost host;
    private final File dir;
    /** The CPU bake of the last {@code skycompare}, which its tries share. */
    private SkyImage compareCpu;

    HarnessSkyChecks(HarnessHost host, File dir) {
        this.host = host;
        this.dir = dir;
    }

    /**
     * Bakes the current sky design on the CPU, draws the same program with the sharp sky shader, and logs how far apart
     * the two images are, from {@code skycompare [tries]}.
     */
    Outcome compare(Command command) {
        command.tries(command.arg.isEmpty() ? SKY_TRIES : Integer.parseInt(command.arg));
        SkyImage world = Albedo.skyImage();
        if (!drawsSharp(world)) return waitForSky(command);
        if (compareCpu == null || !compareCpu.design().equals(world.design())) compareCpu = SkyGenerator.bake(world.design(), COMPARE_W, COMPARE_H);
        SkyImage cpu = compareCpu;
        Harness.SkyDraw draw = new Harness.SkyDraw(cpu, cpu.program().block(), COMPARE_W, COMPARE_H, 1, null, new File(dir, "sky-gpu.png"));
        return started(host.drawSharpSky(draw, (gpu, nanos) -> {
            double[] d = HarnessImages.oklabDifference(cpu.argb(), gpu);
            HarnessLog.log(String.format(Locale.ROOT, "skycompare %dx%d: mean %.4f, 99%% %.4f, max %.4f: %s", COMPARE_W, COMPARE_H,
                    d[0], d[1], d[2], d[0] < COMPARE_LIMIT ? "same" : "DIFFERENT"));
        }));
    }

    /**
     * Draws the world sky with the sharp sky shader into a camera view full of sky and logs the GPU time of one draw, from
     * {@code skytime [width height [yaw pitch fov]] [tries]}.
     */
    Outcome time(Command command) {
        String[] args = command.arg.isEmpty() ? new String[0] : command.arg.split("\\s+");
        int given = args.length >= 5 ? 5 : args.length >= 2 ? 2 : 0;
        command.tries(args.length > given ? Integer.parseInt(args[given]) : SKY_TRIES);
        SkyImage world = Albedo.skyImage();
        if (!drawsSharp(world)) return waitForSky(command);
        int width = given > 0 ? Integer.parseInt(args[0]) : TIME_W, height = given > 0 ? Integer.parseInt(args[1]) : TIME_H;
        float yaw = given == 5 ? Float.parseFloat(args[2]) : TIME_YAW, pitch = given == 5 ? Float.parseFloat(args[3]) : TIME_PITCH;
        float fov = given == 5 ? Float.parseFloat(args[4]) : TIME_FOV;
        float[] view = HarnessImages.viewCorners(yaw, pitch, fov, (float) width / height);
        float[] block = world.program().at(SkyClock.longSeconds(), SkyTurn.STILL);
        Harness.SkyDraw draw = new Harness.SkyDraw(world, block, width, height, TIME_REPEAT, view, null);
        return started(host.drawSharpSky(draw, (gpu, nanos) -> {
            if (nanos < 0) HarnessLog.fail("skytime: the GPU gave no time");
            else HarnessLog.log(String.format(Locale.ROOT, "skytime %dx%d: %.3f ms on the GPU for each draw", width, height, nanos / 1e6));
        }));
    }

    private static boolean drawsSharp(SkyImage world) {
        return world != null && world.program().drawsSharp();
    }

    /** The CPU bake of a new sky can still run on a slow machine, so the command waits for it while it has tries left. */
    private static Outcome waitForSky(Command command) {
        if (command.tries(0) > 0) return Outcome.RETRY;
        HarnessLog.fail(command.verb + ": the sky is not a custom sky that the shader can draw");
        return Outcome.DONE;
    }

    /** While the shader compiles, the command runs again on the next tick. */
    private static Outcome started(boolean started) {
        return started ? Outcome.DONE : Outcome.RETRY;
    }
}
