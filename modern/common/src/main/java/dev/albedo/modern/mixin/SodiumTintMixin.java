package dev.albedo.modern.mixin;

//? if >=1.20.5 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Block tint in Sodium and Embeddium chunk meshing, which have their own block renderer (so BlockTintMixin does not
 * run) and tints that do not come from the colormaps (birch and spruce leaves, lily pads, swamp grass); it uses
 * MixinExtras, which Forge does not have before 1.20.5.
 */
@Pseudo // the game starts without these mods, and each renderer version has only one of the methods (require = 0)
@Mixin(targets = {
        "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
        "org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockRenderer"
}, remap = false)
public abstract class SodiumTintMixin {
    /** Sodium 0.8.14 to 0.9.2 (1.21.11, 26.x). */
    @ModifyExpressionValue(method = "tintQuad", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;getTintIndex()I"))
    private int albedo$tintIndex(int tintIndex) {
        return ModernHooks.blockTintIndex(tintIndex);
    }

    /** Sodium 0.6.13 (1.21.5) and 0.7 (1.21.8, 1.21.10). */
    @ModifyExpressionValue(method = "tintQuad", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/frapi/mesh/MutableQuadViewImpl;tintIndex()I"))
    private int albedo$quadTintIndex(int tintIndex) {
        return ModernHooks.blockTintIndex(tintIndex);
    }

    /** Sodium 0.9.3 and later (26.x). */
    @ModifyExpressionValue(method = "tintQuad", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;sodium$getTintIndex()I"))
    private int albedo$sodiumTintIndex(int tintIndex) {
        return ModernHooks.blockTintIndex(tintIndex);
    }

    /** Sodium 0.8.13 and earlier (1.21.1). */
    @ModifyVariable(method = "colorizeQuad", require = 0, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int albedo$colorIndex(int tintIndex) {
        return ModernHooks.blockTintIndex(tintIndex);
    }

    /** Embeddium (1.21.1). */
    @ModifyExpressionValue(method = "getVertexColors", require = 0, at = @At(value = "INVOKE",
            target = "Lorg/embeddedt/embeddium/impl/model/quad/BakedQuadView;hasColor()Z"))
    private boolean albedo$hasColor(boolean tinted) {
        return ModernHooks.blockTinted(tinted);
    }
}
//?}
