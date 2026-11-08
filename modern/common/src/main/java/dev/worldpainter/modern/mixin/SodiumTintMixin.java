package dev.worldpainter.modern.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.worldpainter.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Block tint in Sodium and Embeddium chunk meshing. They have their own block renderer, so BlockTintMixin does not run,
 * and some tints (birch and spruce leaves, lily pads, swamp grass) do not come from the colormaps. The handlers mark
 * the quad as not tinted, so the renderer skips the color provider.
 * Optional: @Pseudo, so the game starts without these mods. The targets contain no Minecraft types, so remap = false.
 * Each renderer version has only one of the methods (require = 0).
 */
@Pseudo
@Mixin(targets = {
        "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
        "org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockRenderer"
}, remap = false)
public abstract class SodiumTintMixin {
    /** Sodium 0.8.14 to 0.9.2 (1.21.11, 26.x). */
    @ModifyExpressionValue(method = "tintQuad", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;getTintIndex()I"))
    private int worldpainter$tintIndex(int tintIndex) {
        return ModernHooks.blockTintIndex(tintIndex);
    }

    /** Sodium 0.9.3 and later (26.x). */
    @ModifyExpressionValue(method = "tintQuad", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;sodium$getTintIndex()I"))
    private int worldpainter$sodiumTintIndex(int tintIndex) {
        return ModernHooks.blockTintIndex(tintIndex);
    }

    /** Sodium 0.8.13 and earlier (1.21.1). */
    @ModifyVariable(method = "colorizeQuad", require = 0, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int worldpainter$colorIndex(int tintIndex) {
        return ModernHooks.blockTintIndex(tintIndex);
    }

    /** Embeddium (1.21.1). */
    @ModifyExpressionValue(method = "getVertexColors", require = 0, at = @At(value = "INVOKE",
            target = "Lorg/embeddedt/embeddium/impl/model/quad/BakedQuadView;hasColor()Z"))
    private boolean worldpainter$hasColor(boolean tinted) {
        return ModernHooks.blockTinted(tinted);
    }
}
