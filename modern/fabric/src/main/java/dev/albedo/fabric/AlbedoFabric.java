package dev.albedo.fabric;

import dev.albedo.modern.ModernHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** The whole Fabric-specific surface, which needs only Fabric Loader (no Fabric API). */
public final class AlbedoFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModernHooks.bootstrap(FabricLoader.getInstance().getConfigDir().toFile());
    }
}
