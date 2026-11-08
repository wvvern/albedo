package dev.worldpainter.neoforge;

import dev.worldpainter.core.WorldPainter;
import dev.worldpainter.modern.ModernHooks;
import dev.worldpainter.modern.SettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** The whole NeoForge-specific surface: start the mod and give it a "Config" button in the mod list. */
@Mod(value = WorldPainter.MOD_ID, dist = Dist.CLIENT)
public final class WorldPainterNeoForge {
    public WorldPainterNeoForge(ModContainer container) {
        ModernHooks.bootstrap();
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
    }
}
