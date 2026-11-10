package dev.albedo.modern;

//? if >=1.19 {
import net.minecraft.client.Minecraft;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
//? if >=1.19.3 {
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
//?} else {
/*import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.DataPackConfig;
*///?}
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.File;

/** Development-only: opens the flat creative world of the test harness, and makes it at the first run. */
final class HarnessWorlds {
    private HarnessWorlds() {}

    static void open(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (new File(new File(mc.gameDirectory, "saves"), name).isDirectory()) {
            //? if >=1.20.5 {
            mc.createWorldOpenFlows().openWorld(name, () -> ModernPlatform.setScreen(null));
            //?} elif >=1.20.3 {
            /*mc.createWorldOpenFlows().checkForBackupAndLoad(name, () -> ModernPlatform.setScreen(null));
            *///?} else {
            /*mc.createWorldOpenFlows().loadLevel(ModernPlatform.currentScreen(), name);
            *///?}
            return;
        }
        //? if >=26.1 {
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        //?} elif >=1.19.3 {
        /*LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, gameRules(),
                WorldDataConfiguration.DEFAULT);
        *///?} else {
        /*LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, gameRules(),
                DataPackConfig.DEFAULT);
        *///?}
        //? if >=1.20.3 {
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(42L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                ModernPlatform.currentScreen());
        //?} elif >=1.19.3 {
        /*mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(42L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        *///?} else {
        /*RegistryAccess registries = RegistryAccess.BUILTIN.get();
        mc.createWorldOpenFlows().createFreshLevel(name, settings, registries,
                registries.registryOrThrow(Registry.WORLD_PRESET_REGISTRY).getOrThrow(WorldPresets.FLAT).createWorldGenSettings(42L, false, false));
        *///?}
    }

    /** The default game rules of a new world, which 26.1 and later make in the level settings. */
    //? if >=1.21.11 {
    private static net.minecraft.world.level.gamerules.GameRules gameRules() {
        return new net.minecraft.world.level.gamerules.GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures());
    }
    //?} elif >=1.21.2 {
    /*private static net.minecraft.world.level.GameRules gameRules() {
        return new net.minecraft.world.level.GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures());
    }
    *///?} else {
    /*private static net.minecraft.world.level.GameRules gameRules() {
        return new net.minecraft.world.level.GameRules();
    }
    *///?}
}
//?}
