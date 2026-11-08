package dev.singlecolor.legacy;

import dev.singlecolor.core.SingleColor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** The whole Forge-specific surface for 1.8.9; everything else is mixins + the core. */
@Mod(modid = SingleColor.MOD_ID, name = SingleColor.NAME, version = "0.1.0",
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class SingleColorForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LegacyHooks.bootstrap();
    }
}
