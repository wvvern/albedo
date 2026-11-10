package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hidden clouds: the renderer reads the cloud option as off in each frame, so a change of the profile shows at once. */
@Mixin(Options.class)
public abstract class CloudsMixin {
    //? if >=26.1 {
    @Inject(method = "getCloudStatus", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "getCloudsType", at = @At("RETURN"), cancellable = true)
    *///?}
    private void albedo$clouds(CallbackInfoReturnable<CloudStatus> cir) {
        cir.setReturnValue(ModernHooks.cloudStatus(cir.getReturnValue()));
    }
}
