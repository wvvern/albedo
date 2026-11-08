package dev.blockpainter.neoforge;

import dev.blockpainter.core.BlockPainter;
import dev.blockpainter.modern.ModernHooks;
import dev.blockpainter.modern.SettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** The whole NeoForge-specific surface: start the mod and give it a "Config" button in the mod list. */
@Mod(value = BlockPainter.MOD_ID, dist = Dist.CLIENT)
public final class BlockPainterNeoForge {
    public BlockPainterNeoForge(ModContainer container) {
        ModernHooks.bootstrap();
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
    }
}
