package dev.albedo.modern.mixin;

//? if >=1.21.2 && <1.21.6 {
/*import dev.albedo.modern.GlSky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Draws the custom sky through the raw GL in place of the sky disc of SkyRenderer, and leaves out the other sky parts
 * as {@code SkyLayersMixin} does; the parameters change between the versions, so the handlers take none of them.
 ^/
@Mixin(net.minecraft.client.renderer.SkyRenderer.class)
public abstract class GlSkyLayersMixin {
    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void albedo$sky(CallbackInfo ci) {
        if (GlSky.drawDisc()) ci.cancel();
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void albedo$darkDisc(CallbackInfo ci) {
        if (GlSky.replacedDisc()) ci.cancel();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void albedo$sunrise(CallbackInfo ci) {
        if (GlSky.replacedDisc()) ci.cancel();
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void albedo$stars(CallbackInfo ci) {
        if (GlSky.hidesStars()) ci.cancel();
    }
}
*///?}
