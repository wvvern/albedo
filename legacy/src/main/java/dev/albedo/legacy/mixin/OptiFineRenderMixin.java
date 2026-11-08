package dev.albedo.legacy.mixin;

import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BlockModelRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Block tint, fluid tint, quads into the chunk mesh and fluid face shade with OptiFine (MixinPlugin applies it only
 * with OptiFine). OptiFine replaces the quad methods of BlockModelRenderer and makes FaceBakery.getFaceBrightness
 * static. The names and signatures are different in each OptiFine version, so an injector can fail and stop the game.
 * Thus this mixin has no members: OptiFineRenderPatch finds the calls by their target and sends them through
 * LegacyHooks.
 */
@Mixin({BlockModelRenderer.class, BlockFluidRenderer.class})
public abstract class OptiFineRenderMixin {
}
