package dev.albedo.modern;

import dev.albedo.core.dev.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.Options;
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
        HarnessWorlds.open(name);
    }

    @Override
    public void runCommand(String command) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            Harness.log("! no singleplayer server");
            return;
        }
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    @Override
    public void screenshot(File png) {
        HarnessShots.take(png);
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
        String texture = id.equals("steve") ? net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture().toString() : id;
        ModernPlatform.setScreen(new TextureScreen(texture));
    }

    @Override
    public void quiet() {
        Options o = Minecraft.getInstance().options;
        if (o.getSoundSourceOptionInstance(SoundSource.MASTER).get() != 0.0) o.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        if (o.narrator().get() != NarratorStatus.OFF) o.narrator().set(NarratorStatus.OFF);
        // A window on a hidden desktop never has focus; pausing would stop the command queue.
        o.pauseOnLostFocus = false;
    }

    @Override
    public void setFov(int degrees) {
        Minecraft.getInstance().options.fov().set(degrees);
    }

    @Override
    public void look(float yaw, float pitch) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
    }

    @Override
    public boolean drawSharpSky(dev.albedo.core.sky.SkyImage sky, float[] block, int width, int height, int repeat, float[] view, File png,
                                Harness.SkyDone done) {
        //? if >=26.3 {
        return SharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
        //?} elif >=1.21.11 {
        /*return PrecompiledSharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
        *///?} else {
        /*return GlSharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
        *///?}
    }

    @Override
    public int framesPerSecond() {
        return Minecraft.getInstance().getFps();
    }

    @Override
    public void uncapFrameRate() {
        Options o = Minecraft.getInstance().options;
        // The option value 260 is "Unlimited".
        o.framerateLimit().set(260);
        o.enableVsync().set(false);
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
        var window = Minecraft.getInstance().getWindow();
        return new int[]{window.getWidth(), window.getHeight()};
    }
}
