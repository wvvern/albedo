package dev.albedo.modern;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.dev.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;
import java.io.IOException;

/** Development-only: saves a screenshot of the frame for the test harness. */
final class HarnessShots {
    private HarnessShots() {}

    static void take(File png) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.1 {
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        //?} else {
        /*RenderTarget target = mc.getMainRenderTarget();
        *///?}
        //? if >=1.21.5 {
        Screenshot.takeScreenshot(target, image -> save(image, png));
        //?} else {
        /*save(Screenshot.takeScreenshot(target), png);
        *///?}
    }

    private static void save(NativeImage image, File png) {
        new Thread(() -> {
            try (NativeImage owned = image) {
                owned.writeToFile(png);
                Harness.log("saved " + png.getName());
            } catch (IOException e) {
                Harness.log("! screenshot failed: " + e);
            }
        }, "albedo-screenshot").start();
    }
}
