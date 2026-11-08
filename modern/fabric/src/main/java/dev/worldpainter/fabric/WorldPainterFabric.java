package dev.worldpainter.fabric;

import dev.worldpainter.modern.ModernHooks;
import net.fabricmc.api.ClientModInitializer;

/** The whole Fabric-specific surface. Needs only Fabric Loader (no Fabric API). */
public final class WorldPainterFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModernHooks.bootstrap();
    }
}
