package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The start and the end of each frame, which draws the world and the screens, for the Chroma pass on the atlases. */
@Mixin(GameRenderer.class)
public abstract class FrameMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void albedo$frameStart(CallbackInfo ci) {
        ModernHooks.frameStart();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void albedo$frameEnd(CallbackInfo ci) {
        ModernHooks.frameEnd();
    }
}
