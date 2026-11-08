package dev.blockpainter.modern.mixin;

import dev.blockpainter.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Since 1.21.2 clouds.png is read straight by CloudRenderer as cell data rather than as a texture, so hiding clouds
 * means handing it no data. On 1.21.1 the class doesn't exist (@Pseudo makes this a no-op) and the texture hook
 * already makes clouds transparent.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.CloudRenderer")
public abstract class CloudsMixin {
    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void blockpainter$hide(CallbackInfoReturnable<Optional<?>> cir) {
        if (ModernHooks.cloudsHidden()) cir.setReturnValue(Optional.empty());
    }
}
