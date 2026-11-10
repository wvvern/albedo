package dev.albedo.legacy.mixin;

import dev.albedo.legacy.Game;
import dev.albedo.legacy.LegacyHooks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.util.math.BlockPos;
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
    @Redirect(method = {"renderQuadsSmooth", "renderQuadsFlat"}, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/color/BlockColors;colorMultiplier("
                    + "Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;I)I"))
    private int albedo$tint(BlockColors colors, IBlockState state, IBlockAccess world, BlockPos pos, int tintIndex) {
        LegacyHooks.TINT.run();
        return LegacyHooks.blockTint(colors.colorMultiplier(state, world, pos, tintIndex));
    }

    @Redirect(method = {"renderQuadsSmooth", "renderQuadsFlat"}, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;addVertexData([I)V"))
    private void albedo$quad(BufferBuilder buffer, int[] vertexData) {
        LegacyHooks.SHADE.run();
        Game.addQuad(buffer, vertexData);
    }
}
