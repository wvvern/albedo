package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraft.client.multiplayer.GuiConnecting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the host of each connection, because Realms and the --server argument do not set the current server data. */
@Mixin(GuiConnecting.class)
public abstract class GuiConnectingMixin {
    @Inject(method = "connect", at = @At("HEAD"))
    private void albedo$connect(String host, int port, CallbackInfo ci) {
        LegacyHooks.connecting(host);
    }
}
