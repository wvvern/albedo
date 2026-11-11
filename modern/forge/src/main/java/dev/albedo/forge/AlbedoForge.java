package dev.albedo.forge;

import dev.albedo.core.Albedo;
import dev.albedo.modern.ModernHooks;
import dev.albedo.modern.SettingsScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
//? if >=1.19 {
import net.minecraftforge.client.ConfigScreenHandler;
//?}

/** The whole Forge-specific surface: start the mod and give it a "Config" button in the mod list. */
@Mod(Albedo.MOD_ID)
public final class AlbedoForge {
    public AlbedoForge() {
        // A dedicated server loads the mod and does nothing.
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        ModernHooks.bootstrap(FMLPaths.CONFIGDIR.get().toFile());
        //? if >=1.19 {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
        //?} else {
        /*ForgeOldConfig.register();
        *///?}
    }
}
