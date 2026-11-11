package dev.albedo.forge.mixin;

//? if <1.19 {
/*import dev.albedo.modern.ModernHooks;
import net.minecraftforge.client.model.pipeline.BlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The light pipeline of Forge, which a player can turn on, asks this for the tint of a block quad and not BlockModelRenderer.
@Mixin(value = BlockInfo.class, remap = false)
public abstract class ForgeBlockInfoMixin {
    @Inject(method = "getColorMultiplier", at = @At("RETURN"), cancellable = true)
    private void albedo$tint(int tint, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ModernHooks.blockTint(cir.getReturnValue()));
    }
}
*///?}
