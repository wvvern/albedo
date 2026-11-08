package dev.blockpainter.legacy;

import dev.blockpainter.core.BlockPainter;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** The whole Forge-specific surface for 1.8.9; everything else is mixins + the core. */
@Mod(modid = BlockPainter.MOD_ID, name = BlockPainter.NAME, useMetadata = true,
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class BlockPainterForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LegacyHooks.bootstrap();
    }
}
