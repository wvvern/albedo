package dev.albedo.modern.harness;

//? if <1.19 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

/^* Development-only: makes the flat creative world of the test harness before the world presets of 1.19; {@code HarnessWorlds} opens it. ^/
public final class HarnessWorldsBeforePresets {
    private HarnessWorldsBeforePresets() {}

    public static void create(String name) {
        Minecraft mc = Minecraft.getInstance();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), DataPackConfig.DEFAULT);
        //? if >=1.18 {
        RegistryAccess registries = RegistryAccess.BUILTIN.get();
        Registry<net.minecraft.world.level.levelgen.structure.StructureSet> structures = registries.registryOrThrow(Registry.STRUCTURE_SET_REGISTRY);
        ChunkGenerator generator = new FlatLevelSource(structures,
                FlatLevelGeneratorSettings.getDefault(registries.registryOrThrow(Registry.BIOME_REGISTRY), structures));
        WorldGenSettings generation = new WorldGenSettings(42L, false, false, WorldGenSettings.withOverworld(
                registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY), DimensionType.defaultDimensions(registries, 42L), generator));
        //?} else {
        /^RegistryAccess.RegistryHolder registries = RegistryAccess.builtin();
        ChunkGenerator generator = new FlatLevelSource(FlatLevelGeneratorSettings.getDefault(registries.registryOrThrow(Registry.BIOME_REGISTRY)));
        WorldGenSettings generation = new WorldGenSettings(42L, false, false, WorldGenSettings.withOverworld(
                registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY), DimensionType.defaultDimensions(registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY),
                registries.registryOrThrow(Registry.BIOME_REGISTRY), registries.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY), 42L), generator));
        ^///?}
        mc.createLevel(name, settings, registries, generation);
    }
}
*///?}
