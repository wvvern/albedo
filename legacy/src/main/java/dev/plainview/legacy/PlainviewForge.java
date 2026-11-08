package dev.plainview.legacy;

import dev.plainview.core.Plainview;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** The whole Forge-specific surface for 1.8.9; everything else is mixins + the core. */
@Mod(modid = Plainview.MOD_ID, name = Plainview.NAME, useMetadata = true,
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class PlainviewForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LegacyHooks.bootstrap();
    }
}
