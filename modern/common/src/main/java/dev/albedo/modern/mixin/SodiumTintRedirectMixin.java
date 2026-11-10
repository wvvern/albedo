package dev.albedo.modern.mixin;

//? if <1.20.5 {
/*import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Block tint in Sodium 0.5 and Embeddium 0.3 for 1.20.1, with plain Mixin because Forge has no MixinExtras before 1.20.5:
// the vertex colors of a quad are white where the tint is removed. The game starts without these mods (require = 0).
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumTintRedirectMixin {
    @Inject(method = "getVertexColors", require = 0, at = @At("RETURN"))
    private void albedo$tint(CallbackInfoReturnable<int[]> cir) {
        if (!ModernHooks.blockTinted(true)) java.util.Arrays.fill(cir.getReturnValue(), -1);
    }
}
*///?}
