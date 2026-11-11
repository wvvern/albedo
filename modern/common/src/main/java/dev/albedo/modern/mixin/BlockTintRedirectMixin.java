package dev.albedo.modern.mixin;

//? if <1.20.5 {
/*import dev.albedo.modern.ModernHooks;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// The block tint of vanilla chunk meshing with plain Mixin, because Forge has no MixinExtras before 1.20.5, with
// require = 0 so that a mod that redirects the same call does not stop the game.
@Mixin(ModelBlockRenderer.class)
public abstract class BlockTintRedirectMixin {
    @Redirect(method = "putQuadData", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/color/block/BlockColors;getColor(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;I)I"))
    private int albedo$tint(BlockColors colors, BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        return ModernHooks.blockTint(colors.getColor(state, level, pos, tintIndex));
    }
}
*///?}
