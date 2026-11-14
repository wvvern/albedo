package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla block tint call sites (grass, leaves, vines...) and chunk mesh quads (face shade) in methods that Forge's
 * lighting pipeline (on by default, see ForgeVertexLighterMixin) does not use.
 */
@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererVanillaMixin {
    @Redirect(method = {"renderModelAmbientOcclusionQuads", "renderModelStandardQuads"}, require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;colorMultiplier(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;I)I"))
    private int albedo$tint(Block block, IBlockAccess world, BlockPos pos, int tintIndex) {
        return LegacyHooks.tintHook(block.colorMultiplier(world, pos, tintIndex));
    }

    @Redirect(method = {"renderModelAmbientOcclusionQuads", "renderModelStandardQuads"}, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldRenderer;addVertexData([I)V"))
    private void albedo$quad(WorldRenderer renderer, int[] vertexData) {
        renderer.addVertexData(LegacyHooks.vanillaQuad(vertexData));
    }
}
