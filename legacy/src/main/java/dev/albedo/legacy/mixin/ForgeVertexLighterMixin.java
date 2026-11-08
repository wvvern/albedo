package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraftforge.client.model.pipeline.VertexLighterFlat;
import net.minecraftforge.client.model.pipeline.VertexLighterSmoothAo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Face shade in Forge's block lighting pipeline (on by default without OptiFine). Each vertex color comes from the
 * baked quad; updateColor multiplies it by the tint (and by smooth lighting). VertexLighterSmoothAo does not call the
 * method of VertexLighterFlat, so both classes are targets.
 */
@Mixin(value = {VertexLighterFlat.class, VertexLighterSmoothAo.class}, remap = false)
public abstract class ForgeVertexLighterMixin {
    @Inject(method = "updateColor", at = @At("HEAD"))
    private void albedo$shade(float[] normal, float[] color, float x, float y, float z, float tint, int multiplier, CallbackInfo ci) {
        LegacyHooks.vertexColor(color);
    }
}
