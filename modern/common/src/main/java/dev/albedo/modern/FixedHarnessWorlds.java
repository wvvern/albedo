package dev.albedo.modern;

//? if gl_fixed {
/*import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

import java.io.File;

// Development-only: opens the flat creative world of the test harness on the versions that load and make a world with Minecraft itself.
final class FixedHarnessWorlds {
    private FixedHarnessWorlds() {}

    static void open(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (new File(new File(mc.gameDirectory, "saves"), name).isDirectory()) {
            mc.loadLevel(name);
            return;
        }
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), DataPackConfig.DEFAULT);
        RegistryAccess.RegistryHolder registries = RegistryAccess.builtin();
        Registry<Biome> biomes = registries.registryOrThrow(Registry.BIOME_REGISTRY);
        Registry<DimensionType> types = registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY);
        WorldGenSettings generator = new WorldGenSettings(42L, false, false, WorldGenSettings.withOverworld(types,
                DimensionType.defaultDimensions(types, biomes, registries.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY), 42L),
                new FlatLevelSource(FlatLevelGeneratorSettings.getDefault(biomes))));
        mc.createLevel(name, settings, registries, generator);
    }
}
*///?}
