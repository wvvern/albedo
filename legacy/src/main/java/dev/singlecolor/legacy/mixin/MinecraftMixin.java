package dev.singlecolor.legacy.mixin;

import dev.singlecolor.legacy.LegacyHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client tick without the event bus: drives toggle keys and config-file watching. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "runTick", at = @At("TAIL"))
    private void singlecolor$tick(CallbackInfo ci) {
        LegacyHooks.clientTick();
    }
}
