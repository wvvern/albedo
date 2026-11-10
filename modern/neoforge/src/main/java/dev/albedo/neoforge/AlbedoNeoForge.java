package dev.albedo.neoforge;

import dev.albedo.core.Albedo;
import dev.albedo.modern.ModernHooks;
import dev.albedo.modern.SettingsScreen;
import net.neoforged.api.distmarker.Dist;
//? if >=1.20.5 {
import net.neoforged.fml.ModContainer;
//?} else {
/*import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.ConfigScreenHandler;
*///?}
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
//? if >=1.20.5 {
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
//?}

/** The whole NeoForge-specific surface: start the mod and give it a "Config" button in the mod list. */
//? if >=1.20.5 {
@Mod(value = Albedo.MOD_ID, dist = Dist.CLIENT)
public final class AlbedoNeoForge {
    public AlbedoNeoForge(ModContainer container) {
        ModernHooks.bootstrap(FMLPaths.CONFIGDIR.get().toFile());
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
    }
}
//?} else {
/*@Mod(Albedo.MOD_ID)
public final class AlbedoNeoForge {
    public AlbedoNeoForge() {
        // A dedicated server loads the mod and does nothing.
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        ModernHooks.bootstrap(FMLPaths.CONFIGDIR.get().toFile());
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
    }
}
*///?}
