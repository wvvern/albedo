package dev.worldpainter.modern;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import dev.worldpainter.core.dev.Harness;
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

    private static void save(NativeImage image, File png) {
        new Thread(() -> {
            try (NativeImage owned = image) {
                owned.writeToFile(png);
                Harness.log("saved " + png.getName());
            } catch (IOException e) {
                Harness.log("! screenshot failed: " + e);
            }
        }, "worldpainter-screenshot").start();
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
    public void closeScreen() {
        ModernPlatform.setScreen(null);
    }

    @Override
    public void quit() {
        Minecraft.getInstance().stop();
    }

    @Override
    public void quiet() {
        Options o = Minecraft.getInstance().options;
        if (o.getSoundSourceOptionInstance(SoundSource.MASTER).get() != 0.0) o.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        if (o.narrator().get() != NarratorStatus.OFF) o.narrator().set(NarratorStatus.OFF);
        // A window on a hidden desktop never has focus; pausing would stop the command queue.
        o.pauseOnLostFocus = false;
    }
}
