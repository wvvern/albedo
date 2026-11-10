package dev.albedo.forge;

import dev.albedo.core.Albedo;
import dev.albedo.modern.ModernHooks;
import dev.albedo.modern.SettingsScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
//? if >=1.19 {
import net.minecraftforge.client.ConfigScreenHandler;
//?} else {
/*import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.commons.lang3.tuple.Pair;
*///?}

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
        //?} else {
        /*// A server without the mod stays compatible in the server list, as displayTest does from Forge 41.
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST,
                () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (version, network) -> true));
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY, () -> (mc, parent) -> new SettingsScreen(parent));
        *///?}
    }
}
