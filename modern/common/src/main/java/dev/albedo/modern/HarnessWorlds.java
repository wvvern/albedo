package dev.albedo.modern;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.File;

/** Development-only: opens the flat creative world of the test harness, and makes it at the first run. */
final class HarnessWorlds {
    private HarnessWorlds() {}

    static void open(String name) {
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
}
