package dev.worldpainter.modern.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.worldpainter.modern.ModernHooks;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Where vanilla chunk meshing applies block tints (grass, leaves, vines, redstone...). The handler only sees the
 * int, so no version-specific type appears in it; only the target string changes.
 * require = 0: other renderers (e.g. Sodium) may replace this method; the colormap hooks still apply then.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class BlockTintMixin {
    //? if >=26.1 {
    @ModifyExpressionValue(method = "computeTintColor", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/color/block/BlockTintSource;colorInWorld(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"))
    //?} else {
    /*@ModifyExpressionValue(method = "putQuadData", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/color/block/BlockColors;getColor(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;I)I"))
    *///?}
    private int worldpainter$tint(int original) {
        return ModernHooks.blockTint(original);
    }
}
