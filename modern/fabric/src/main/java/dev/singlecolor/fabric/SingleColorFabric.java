package dev.singlecolor.fabric;

import dev.singlecolor.modern.ModernHooks;
import net.fabricmc.api.ClientModInitializer;

/** The whole Fabric-specific surface. Needs only Fabric Loader (no Fabric API). */
public final class SingleColorFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModernHooks.bootstrap();
    }
}
