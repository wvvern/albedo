package dev.albedo.forge;

import dev.albedo.core.Albedo;
import dev.albedo.modern.ModernHooks;
import dev.albedo.modern.SettingsScreen;
import net.minecraftforge.api.distmarker.Dist;
//? if >=1.19 {
import net.minecraftforge.client.ConfigScreenHandler;
//?} elif >=1.18 {
/*import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.network.NetworkConstants;
*///?} else {
/*import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fmlclient.ConfigGuiHandler;
import net.minecraftforge.fmllegacy.network.FMLNetworkConstants;
*///?}
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

/** The whole Forge-specific surface: start the mod and give it a "Config" button in the mod list. */
@Mod(Albedo.MOD_ID)
public final class AlbedoForge {
    public AlbedoForge() {
        // A dedicated server loads the mod and does nothing.
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        ModernHooks.bootstrap(FMLPaths.CONFIGDIR.get().toFile());
        //? if >=1.19 {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
        //?} elif >=1.18 {
        /*// A server without the mod stays compatible in the server list.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
        ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory((mc, parent) -> new SettingsScreen(parent)));
        *///?} else {
        /*// A server without the mod stays compatible in the server list.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> FMLNetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
        ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory((mc, parent) -> new SettingsScreen(parent)));
        *///?}
    }
}
