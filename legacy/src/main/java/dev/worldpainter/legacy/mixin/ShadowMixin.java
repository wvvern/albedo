package dev.worldpainter.legacy.mixin;

import dev.worldpainter.legacy.LegacyHooks;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Entity shadows off: the renderer's "entity shadows" setting reads false. */
@Mixin(Render.class)
public abstract class ShadowMixin {
    @Redirect(method = "doRenderShadowAndFire", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;entityShadows:Z"))
    private boolean worldpainter$shadows(GameSettings settings) {
        return settings.entityShadows && LegacyHooks.shadows();
    }
}
