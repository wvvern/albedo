package dev.albedo.neoforge;

import dev.albedo.core.Albedo;
import dev.albedo.modern.ModernHooks;
import dev.albedo.modern.SettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** The whole NeoForge-specific surface: start the mod and give it a "Config" button in the mod list. */
@Mod(value = Albedo.MOD_ID, dist = Dist.CLIENT)
public final class AlbedoNeoForge {
    public AlbedoNeoForge(ModContainer container) {
        ModernHooks.bootstrap(FMLPaths.CONFIGDIR.get().toFile());
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
    }
}
