package dev.worldpainter.legacy;

import dev.worldpainter.core.WorldPainter;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** The whole Forge-specific surface for 1.8.9; everything else is mixins + the core. */
@Mod(modid = WorldPainter.MOD_ID, name = WorldPainter.NAME, useMetadata = true,
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class WorldPainterForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LegacyHooks.bootstrap();
    }
}
