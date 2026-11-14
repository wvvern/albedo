package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block tint in the older Sodium, Embeddium and Rubidium chunk meshing (1.16.5 to 1.20.6), which have their own block
 * renderer and use plain Mixin, because Forge has no MixinExtras before 1.20.5; each handler is one renderer generation,
 * and the game starts without these mods (require = 0).
 */
@Pseudo
@Mixin(targets = {
        "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
        "me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer"
}, remap = false)
public abstract class SodiumTintRedirectMixin {
    /** Sodium 0.5 (1.20.1 to 1.20.6), Embeddium 0.3 and Rubidium 0.7: the vertex colors of a quad are white where the tint is removed. */
    @Inject(method = "getVertexColors", require = 0, at = @At("RETURN"))
    private void albedo$tint(CallbackInfoReturnable<int[]> cir) {
        ModernHooks.whiteVertexColors(cir.getReturnValue());
    }

    /** Sodium 0.4 (1.18.2 to 1.19.4), Embeddium 0.3.18 and Rubidium 0.5 to 0.6: the blended colors of a quad are white where the tint is removed. */
    @ModifyVariable(method = {"renderQuad", "renderQuadList"}, require = 0, at = @At(value = "INVOKE_ASSIGN",
            target = "Lme/jellysquid/mods/sodium/client/model/quad/blender/ColorBlender;getColors"))
    private int[] albedo$blendedColors(int[] colors) {
        return ModernHooks.untintedColors(colors);
    }

    /** Sodium 0.2 and 0.3 (1.16.5, 1.17.1), Embeddium 0.3.18 and Rubidium 0.2 (1.16.5): same, with the older blender type. */
    @ModifyVariable(method = "renderQuad", require = 0, at = @At(value = "INVOKE_ASSIGN",
            target = "Lme/jellysquid/mods/sodium/client/model/quad/blender/BiomeColorBlender;getColors"))
    private int[] albedo$biomeColors(int[] colors) {
        return ModernHooks.untintedColors(colors);
    }
}
