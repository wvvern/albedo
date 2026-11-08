package dev.albedo.legacy.mixin;

import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BlockModelRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Block tint, fluid tint, chunk mesh quads and fluid face shade with OptiFine, with no members because an injector can
 * fail and stop the game on OptiFine names and signatures, which differ in each version, so OptiFineRenderPatch
 * finds the calls by their target.
 */
@Mixin({BlockModelRenderer.class, BlockFluidRenderer.class})
public abstract class OptiFineRenderMixin {
}
