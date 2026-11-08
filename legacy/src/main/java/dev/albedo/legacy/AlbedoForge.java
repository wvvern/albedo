package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** The whole Forge-specific surface for 1.8.9; everything else is mixins + the core. */
@Mod(modid = Albedo.MOD_ID, name = Albedo.NAME, useMetadata = true,
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class AlbedoForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LegacyHooks.bootstrap();
    }
}
