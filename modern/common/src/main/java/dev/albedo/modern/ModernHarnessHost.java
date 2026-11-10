package dev.albedo.modern;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.dev.Harness;
import dev.albedo.modern.mixin.MouseHandlerInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.File;
import java.io.IOException;

/** Development-only: what the test harness needs from 1.21+ (see {@link Harness}). */
final class ModernHarnessHost implements Harness.Host {
    @Override
    public boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null;
    }

    @Override
    public void openWorld(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (new File(new File(mc.gameDirectory, "saves"), name).isDirectory()) {
            mc.createWorldOpenFlows().openWorld(name, () -> ModernPlatform.setScreen(null));
            return;
        }
        //? if >=26.1 {
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        //?} elif >=1.21.11 {
        /*LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new net.minecraft.world.level.gamerules.GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()),
                WorldDataConfiguration.DEFAULT);
        *///?} else {
        /*LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new net.minecraft.world.level.GameRules(), WorldDataConfiguration.DEFAULT);
        *///?}
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(42L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                ModernPlatform.currentScreen());
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

    @Override
    public void framePixels(Harness.FramePixels done) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.1 {
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        //?} else {
        /*RenderTarget target = mc.getMainRenderTarget();
        *///?}
        //? if >=1.21.5 {
        Screenshot.takeScreenshot(target, image -> pixels(image, done));
        //?} else {
        /*pixels(Screenshot.takeScreenshot(target), done);
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
                Harness.log("saved " + png.getName());
            } catch (IOException e) {
                Harness.log("! screenshot failed: " + e);
            }
        }, "albedo-screenshot").start();
    }

    @Override
    public void setHudVisible(boolean visible) {
        //? if >=26.1 {
        net.minecraft.client.gui.Hud hud = Minecraft.getInstance().gui.hud;
        if (hud.isHidden() == visible) hud.toggle();
        //?} else {
        /*Minecraft.getInstance().options.hideGui = !visible;
        *///?}
    }

    @Override
    public boolean timeChroma(int frames) {
        return ChromaAtlases.time(frames);
    }

    @Override
    public void closeScreen() {
        ModernPlatform.setScreen(null);
    }

    /** Calls the mouse handler's window callbacks, so that the press goes through the game's own path to the screen. */
    @Override
    public boolean mouseButton(int guiX, int guiY, boolean down) {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        var mouse = (MouseHandlerInvoker) mc.mouseHandler;
        // The center of the GUI pixel, in window pixels, which the handler scales back to GUI pixels.
        double x = (guiX + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth();
        double y = (guiY + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight();
        int action = down ? InputConstants.PRESS : InputConstants.RELEASE;
        //? if >=1.21.11 {
        long handle = window.handle();
        //?} else {
        /*long handle = window.getWindow();
        *///?}
        //? if >=26.1 {
        mouse.albedo$onMove(handle, x, y, 0, 0);
        //?} else {
        /*mouse.albedo$onMove(handle, x, y);
        *///?}
        //? if >=1.21.9 {
        mouse.albedo$onButton(handle, new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), action);
        //?} else {
        /*mouse.albedo$onPress(handle, InputConstants.MOUSE_BUTTON_LEFT, action, 0);
        *///?}
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
        Minecraft mc = Minecraft.getInstance();
        String folder = cape ? "capes/" : "skins/";
        // The game names a skin by the SHA-1 of its hash and keeps the file in <cache>/<first 2>/<hash>.
        var id = id("minecraft:" + folder + com.google.common.hash.Hashing.sha1().hashUnencodedChars(hash));
        File file = new File(new File(new File(png.getParentFile(), "skin-cache"), hash.substring(0, 2)), hash);
        java.nio.file.Files.createDirectories(file.getParentFile().toPath());
        java.nio.file.Files.copy(png.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String url = "http://127.0.0.1:9/" + hash; // never read, because the cache file exists
        //? if >=1.21.4 {
        new net.minecraft.client.renderer.texture.SkinTextureDownloader(java.net.Proxy.NO_PROXY, mc.getTextureManager(), mc)
                .downloadAndRegisterSkin(id, file.toPath(), url, !cape);
        //?} else {
        /*mc.getTextureManager().register(id, new net.minecraft.client.renderer.texture.HttpTexture(file, url,
                net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture(), !cape, null));
        *///?}
        return id.toString();
    }

    @Override
    public void releaseTexture(String id) {
        Minecraft.getInstance().getTextureManager().release(id(id));
    }

    @Override
    public void showTexture(String id) {
        String texture = id.equals("steve") ? net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture().toString() : id;
        ModernPlatform.setScreen(new TextureScreen(texture));
    }

    //? if >=1.21.11 {
    static net.minecraft.resources.Identifier id(String id) {
        return net.minecraft.resources.Identifier.parse(id);
    }
    //?} else {
    /*static net.minecraft.resources.ResourceLocation id(String id) {
        return net.minecraft.resources.ResourceLocation.parse(id);
    }
    *///?}

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
        //? if >=26.1 {
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
        var window = Minecraft.getInstance().getWindow();
        // With no border the frame can be as large as the screen.
        //? if >=26.1 {
        long handle = window.handle();
        org.lwjgl.sdl.SDLVideo.SDL_SetWindowBordered(handle, false);
        org.lwjgl.sdl.SDLVideo.SDL_SetWindowPosition(handle, 0, 0);
        org.lwjgl.sdl.SDLVideo.SDL_SetWindowSize(handle, width, height);
        //?} elif >=1.21.11 {
        /*long handle = window.handle();
        org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(handle, org.lwjgl.glfw.GLFW.GLFW_DECORATED, org.lwjgl.glfw.GLFW.GLFW_FALSE);
        org.lwjgl.glfw.GLFW.glfwSetWindowPos(handle, 0, 0);
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(handle, width, height);
        *///?} else {
        /*long handle = window.getWindow();
        org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(handle, org.lwjgl.glfw.GLFW.GLFW_DECORATED, org.lwjgl.glfw.GLFW.GLFW_FALSE);
        org.lwjgl.glfw.GLFW.glfwSetWindowPos(handle, 0, 0);
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(handle, width, height);
        *///?}
    }

    @Override
    public int[] frameSize() {
        var window = Minecraft.getInstance().getWindow();
        return new int[]{window.getWidth(), window.getHeight()};
    }

    @Override
    public boolean cloudsDrawn() {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.1 {
        if (mc.options.getCloudStatus() == net.minecraft.client.CloudStatus.OFF) return false;
        return hasCloudCells(mc.levelRenderer.cloudRenderer());
        //?} elif >=1.21.2 {
        /*if (mc.options.getCloudsType() == net.minecraft.client.CloudStatus.OFF) return false;
        return hasCloudCells(mc.levelRenderer.getCloudRenderer());
        *///?} else {
        /*return mc.options.getCloudsType() != net.minecraft.client.CloudStatus.OFF;
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
