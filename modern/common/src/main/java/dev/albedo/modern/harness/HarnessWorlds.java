package dev.albedo.modern.harness;

import net.minecraft.client.Minecraft;
//? if >=1.19 {
import dev.albedo.modern.ModernScreens;
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
//?}

import java.io.File;

/** Development-only: opens the flat creative world of the test harness, and makes it at the first run. */
public final class HarnessWorlds {
    private HarnessWorlds() {}

    public static void open(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (!new File(new File(mc.gameDirectory, "saves"), name).isDirectory()) {
            //? if >=1.19 {
            create(mc, name);
            //?} else {
            /*HarnessWorldsBeforePresets.create(name);
            *///?}
            return;
        }
        //? if >=1.20.5 {
        mc.createWorldOpenFlows().openWorld(name, () -> ModernScreens.setScreen(null));
        //?} elif >=1.20.3 {
        /*mc.createWorldOpenFlows().checkForBackupAndLoad(name, () -> ModernScreens.setScreen(null));
        *///?} elif >=1.19 {
        /*mc.createWorldOpenFlows().loadLevel(ModernScreens.currentScreen(), name);
        *///?} else {
        /*mc.loadLevel(name);
        *///?}
    }

    //? if >=1.19 {
    private static void create(Minecraft mc, String name) {
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
                ModernScreens.currentScreen());
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
    //?}
}
