package dev.blockpainter.fabric;

import dev.blockpainter.modern.ModernHooks;
import net.fabricmc.api.ClientModInitializer;

/** The whole Fabric-specific surface. Needs only Fabric Loader (no Fabric API). */
public final class BlockPainterFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModernHooks.bootstrap();
    }
}
