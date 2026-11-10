package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** The whole Forge-specific surface; everything else is mixins + the core. */
@Mod(modid = Albedo.MOD_ID, name = Albedo.NAME, useMetadata = true,
        clientSideOnly = true, acceptedMinecraftVersions = "[" + net.minecraftforge.common.ForgeVersion.mcVersion + "]")
public class AlbedoForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LegacyHooks.bootstrap();
    }
}
