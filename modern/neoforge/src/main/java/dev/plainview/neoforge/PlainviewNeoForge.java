package dev.plainview.neoforge;

import dev.plainview.core.Plainview;
import dev.plainview.modern.ModernHooks;
import dev.plainview.modern.SettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** The whole NeoForge-specific surface: start the mod and give it a "Config" button in the mod list. */
@Mod(value = Plainview.MOD_ID, dist = Dist.CLIENT)
public final class PlainviewNeoForge {
    public PlainviewNeoForge(ModContainer container) {
        ModernHooks.bootstrap();
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
    }
}
