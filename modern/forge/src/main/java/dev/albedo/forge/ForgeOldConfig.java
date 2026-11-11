package dev.albedo.forge;

//? if <1.19 {
/*import dev.albedo.modern.SettingsScreen;
import net.minecraftforge.fml.ModLoadingContext;
//? if >=1.18 {
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.network.NetworkConstants;
//?} elif >=1.17 {
/^import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fmlclient.ConfigGuiHandler;
import net.minecraftforge.fmllegacy.network.FMLNetworkConstants;
^///?} else {
/^import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.commons.lang3.tuple.Pair;
^///?}

/^* The mod list button and the server list rule of the Forge versions before 1.19, which have no ConfigScreenHandler. ^/
final class ForgeOldConfig {
    private ForgeOldConfig() {}

    static void register() {
        // A server without the mod stays compatible in the server list.
        //? if >=1.18 {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
        ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory((mc, parent) -> new SettingsScreen(parent)));
        //?} elif >=1.17 {
        /^ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> FMLNetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
        ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory((mc, parent) -> new SettingsScreen(parent)));
        ^///?} else {
        /^ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST,
                () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (version, network) -> true));
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY, () -> (mc, parent) -> new SettingsScreen(parent));
        ^///?}
    }
}
*///?}
