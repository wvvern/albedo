package dev.albedo.fabric;

import dev.albedo.modern.ModernHooks;
import net.fabricmc.api.ClientModInitializer;

/** The whole Fabric-specific surface. Needs only Fabric Loader (no Fabric API). */
public final class AlbedoFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModernHooks.bootstrap();
    }
}
