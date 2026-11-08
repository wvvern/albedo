package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Hides clouds by handing CloudRenderer no data, because since 1.21.2 it reads clouds.png as cell data rather than as
 * a texture.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.CloudRenderer")
public abstract class CloudsMixin {
    /** Does nothing on 1.21.1, where the class does not exist and the texture hook already makes clouds transparent. */
    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void albedo$hide(CallbackInfoReturnable<Optional<?>> cir) {
        if (ModernHooks.cloudsHidden()) cir.setReturnValue(Optional.empty());
    }
}
