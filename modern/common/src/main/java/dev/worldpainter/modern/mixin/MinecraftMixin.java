package dev.worldpainter.modern.mixin;

import dev.worldpainter.modern.ModernHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client tick without any loader event API: drives the toggle keys and config-file watching. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void worldpainter$tick(CallbackInfo ci) {
        ModernHooks.clientTick();
    }
}
