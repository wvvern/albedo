package dev.albedo.modern;

import com.mojang.blaze3d.platform.Window;
import dev.albedo.core.Keys;
import dev.albedo.core.dev.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.sounds.SoundSource;

import java.io.File;
import java.io.IOException;

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
    public void openWorld(String name) {
        //? if gl_fixed {
        /*FixedHarnessWorlds.open(name);
        *///?} else {
        HarnessWorlds.open(name);
        //?}
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
    public String loadSkin(File png, String hash, boolean cape) throws IOException {
        return HarnessSkins.load(png, hash, cape);
    }

    @Override
    public void releaseTexture(String id) {
        Minecraft.getInstance().getTextureManager().release(HarnessSkins.id(id));
    }

    @Override
    public void showTexture(String id) {
        String texture = id.equals("steve") ? HarnessSkins.defaultSkin() : id;
        ModernPlatform.setScreen(new TextureScreen(texture));
    }

    @Override
    public void quiet() {
        Options o = Minecraft.getInstance().options;
        //? if >=1.19.3 {
        if (o.getSoundSourceOptionInstance(SoundSource.MASTER).get() != 0.0) o.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        //?} else {
        /*if (o.getSoundSourceVolume(SoundSource.MASTER) != 0f) o.setSoundCategoryVolume(SoundSource.MASTER, 0f);
        *///?}
        //? if >=1.19 {
        if (o.narrator().get() != NarratorStatus.OFF) o.narrator().set(NarratorStatus.OFF);
        //?} else {
        /*o.narratorStatus = NarratorStatus.OFF;
        *///?}
        // A window on a hidden desktop never has focus; pausing would stop the command queue.
        o.pauseOnLostFocus = false;
    }

    @Override
    public void setFov(int degrees) {
        //? if >=1.19 {
        Minecraft.getInstance().options.fov().set(degrees);
        //?} else {
        /*Minecraft.getInstance().options.fov = degrees;
        *///?}
    }

    @Override
    public void look(float yaw, float pitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        //? if >=1.17 {
        player.setYRot(yaw);
        player.setXRot(pitch);
        //?} else {
        /*player.yRot = yaw;
        player.xRot = pitch;
        *///?}
        player.yRotO = yaw;
        player.xRotO = pitch;
    }

    @Override
    public boolean drawSharpSky(dev.albedo.core.sky.SkyImage sky, float[] block, int width, int height, int repeat, float[] view, File png,
                                Harness.SkyDone done) {
        //? if >=26.3 {
        return SharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
        //?} elif >=1.21.6 {
        /*return PrecompiledSharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
        *///?} elif gl_fixed {
        /*return FixedSharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
        *///?} else {
        /*return GlSharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
        *///?}
    }

    @Override
    public int framesPerSecond() {
        //? if >=1.19.3 {
        return Minecraft.getInstance().getFps();
        //?} else {
        /*return dev.albedo.modern.mixin.MinecraftFpsAccessor.albedo$fps();
        *///?}
    }

    @Override
    public void uncapFrameRate() {
        Options o = Minecraft.getInstance().options;
        // The option value 260 is "Unlimited".
        //? if >=1.19 {
        o.framerateLimit().set(260);
        o.enableVsync().set(false);
        //?} else {
        /*o.framerateLimit = 260;
        o.enableVsync = false;
        Minecraft.getInstance().getWindow().updateVsync(false);
        *///?}
        //? if >=1.21.2 {
        o.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
        //?}
    }

    @Override
    public void resizeWindow(int width, int height) {
        HarnessWindow.resize(width, height);
    }

    @Override
    public int[] frameSize() {
        Window window = Minecraft.getInstance().getWindow();
        return new int[]{window.getWidth(), window.getHeight()};
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
