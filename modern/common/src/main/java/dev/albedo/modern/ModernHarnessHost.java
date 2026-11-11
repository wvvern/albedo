package dev.albedo.modern;

import dev.albedo.core.Keys;
import dev.albedo.core.dev.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

import java.io.File;

/**
 * Development-only: what the test harness needs from the game (see {@link Harness}), where the {@code Harness*} classes
 * hold the parts that change the most between versions.
 */
final class ModernHarnessHost implements Harness.Host {
    @Override
    public boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null;
    }

    @Override
    public boolean loading() {
        //? if >=26.2 {
        return Minecraft.getInstance().gui.overlay() != null;
        //?} else {
        /*return Minecraft.getInstance().getOverlay() != null;
        *///?}
    }

    @Override
    public void openWorld(String name) {
        //? if >=1.19 {
        HarnessWorlds.open(name);
        //?} else {
        /*HarnessWorldsOld.open(name);
        *///?}
    }

    @Override
    public void runCommand(String command) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            Harness.log("! no singleplayer server");
            return;
        }
        //? if >=1.19 {
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
        //?} else {
        /*server.execute(() -> server.getCommands().performCommand(server.createCommandSourceStack(), command));
        *///?}
    }

    @Override
    public void screenshot(File png) {
        HarnessShots.take(png);
    }

    @Override
    public void framePixels(Harness.FramePixels done) {
        HarnessShots.pixels(done);
    }

    @Override
    public void setHudVisible(boolean visible) {
        //? if >=26.2 {
        net.minecraft.client.gui.Hud hud = Minecraft.getInstance().gui.hud;
        if (hud.isHidden() == visible) hud.toggle();
        //?} else {
        /*Minecraft.getInstance().options.hideGui = !visible;
        *///?}
    }

    @Override
    public java.util.List<dev.albedo.core.dev.HookCounter> hookCounters() {
        return ModernHooks.COUNTERS;
    }

    @Override
    public boolean timeChroma(int frames) {
        return ChromaAtlases.time(frames);
    }

    @Override
    public void closeScreen() {
        ModernPlatform.setScreen(null);
    }

    @Override
    public boolean mouseButton(int guiX, int guiY, boolean down) {
        HarnessMouse.leftButton(guiX, guiY, down);
        return true;
    }

    @Override
    public boolean key(Keys.Key key, boolean down, boolean ctrl) {
        HarnessKeyboard.key(key, down, ctrl);
        return true;
    }

    @Override
    public boolean character(char c, boolean afterKey) {
        return HarnessKeyboard.character(c);
    }

    @Override
    public boolean settingsOpen() {
        return ModernPlatform.currentScreen() instanceof SettingsScreen;
    }

    @Override
    public void quit() {
        // The dev run of Architectury keeps two thread pools that are not daemons, so the JVM must stop when the game thread ends.
        Thread game = Thread.currentThread();
        Thread exit = new Thread(() -> {
            try {
                game.join();
            } catch (InterruptedException e) {
                return;
            }
            System.exit(0);
        }, "albedo-harness-exit");
        exit.setDaemon(true);
        exit.start();
        Minecraft.getInstance().stop();
    }

    @Override
    public void quiet() {
        HarnessOptions.quiet();
    }

    @Override
    public void setFov(int degrees) {
        HarnessOptions.setFov(degrees);
    }

    @Override
    public boolean drawSharpSky(dev.albedo.core.sky.SkyImage sky, float[] block, int width, int height, int repeat, float[] view, File png,
                                Harness.SkyDone done) {
        //? if gl_fixed {
        /*return FixedSharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
        *///?}
        //? if >=26.3 {
        return SharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
        //?} elif >=1.21.6 {
        /*return PrecompiledSharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
        *///?} elif gl_sky {
        /*return GlSharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
        *///?}
    }

    @Override
    public boolean cloudsDrawn() {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.1 {
        if (mc.options.getCloudStatus() == net.minecraft.client.CloudStatus.OFF) return false;
        //?} else {
        /*if (mc.options.getCloudsType() == net.minecraft.client.CloudStatus.OFF) return false;
        *///?}
        //? if >=26.2 {
        return hasCloudCells(mc.levelRenderer.cloudRenderer());
        //?} elif >=1.21.2 {
        /*return hasCloudCells(mc.levelRenderer.getCloudRenderer());
        *///?} else {
        /*return true;
        *///?}
    }

    /** Whether the cloud renderer holds the cells of clouds.png, which it draws nothing without; the dev run has Mojang names. */
    private static boolean hasCloudCells(Object renderer) {
        for (java.lang.reflect.Field f : renderer.getClass().getDeclaredFields()) {
            if (!f.getType().getSimpleName().equals("TextureData")) continue;
            try {
                f.setAccessible(true);
                return f.get(renderer) != null;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("the cloud renderer has no TextureData field");
    }
}
