package dev.worldpainter.legacy.mixin;

import dev.worldpainter.legacy.LegacyHooks;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Fullbright: the lightmap sees a brightness setting far above the slider's maximum. */
@Mixin(EntityRenderer.class)
public abstract class FullbrightMixin {
    @Redirect(method = "updateLightmap", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;gammaSetting:F"))
    private float worldpainter$gamma(GameSettings settings) {
        return LegacyHooks.gamma(settings.gammaSetting);
    }
}
