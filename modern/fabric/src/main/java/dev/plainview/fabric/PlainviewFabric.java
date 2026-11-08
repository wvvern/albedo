package dev.plainview.fabric;

import dev.plainview.modern.ModernHooks;
import net.fabricmc.api.ClientModInitializer;

/** The whole Fabric-specific surface. Needs only Fabric Loader (no Fabric API). */
public final class PlainviewFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModernHooks.bootstrap();
    }
}
