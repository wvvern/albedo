package dev.albedo.modern;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.dev.HarnessLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;
import java.io.IOException;

/** Development-only: saves a screenshot of the frame for the test harness. */
final class HarnessShots {
    private HarnessShots() {}

    static void take(File png) {
        frame(image -> save(image, png));
    }

    /** Gives the pixels of the frame to {@code done}, on the thread that reads the frame. */
    static void pixels(Harness.FramePixels done) {
        frame(image -> pixels(image, done));
    }

    /** Reads the frame into an image, which {@code use} must close. */
    private static void frame(java.util.function.Consumer<NativeImage> use) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        //?} else {
        /*RenderTarget target = mc.getMainRenderTarget();
        *///?}
        //? if >=1.21.5 {
        Screenshot.takeScreenshot(target, use);
        //?} elif >=1.17 {
        /*use.accept(Screenshot.takeScreenshot(target));
        *///?} else {
        /*use.accept(Screenshot.takeScreenshot(mc.getWindow().getWidth(), mc.getWindow().getHeight(), target));
        *///?}
    }

    private static void pixels(NativeImage image, Harness.FramePixels done) {
        try (NativeImage owned = image) {
            NativeImagePixels pixels = new NativeImagePixels(owned);
            int w = pixels.width(), h = pixels.height();
            int[] argb = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) argb[y * w + x] = pixels.getArgb(x, y);
            }
            done.accept(w, h, argb);
        }
    }

    private static void save(NativeImage image, File png) {
        new Thread(() -> {
            try (NativeImage owned = image) {
                owned.writeToFile(png);
                HarnessLog.log("saved " + png.getName());
            } catch (IOException e) {
                HarnessLog.fail("screenshot failed: " + e);
            }
        }, "albedo-screenshot").start();
    }
}
