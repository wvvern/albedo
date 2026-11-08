package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Vanilla block tint call sites (grass, leaves, vines...). OptiFine changes these methods, see {@link OptiFineRenderMixin}. */
@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererVanillaMixin {
    @Redirect(method = {"renderModelAmbientOcclusionQuads", "renderModelStandardQuads"}, require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;colorMultiplier(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;I)I"))
    private int albedo$tint(Block block, IBlockAccess world, BlockPos pos, int tintIndex) {
        return LegacyHooks.blockTint(block.colorMultiplier(world, pos, tintIndex));
    }
}
