package dev.albedo.modern.mixin;

//? if <1.20.5 {
/*import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Fullbright with plain Mixin, because Forge has no MixinExtras before 1.20.5: each read of an option in the lightmap
// code goes through the hook, with require = 0 so that a mod that redirects the same read does not stop the game.
@Mixin(net.minecraft.client.renderer.LightTexture.class)
public abstract class FullbrightRedirectMixin {
    //? if >=1.19 {
    @Redirect(method = "updateLightTexture", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object albedo$gamma(net.minecraft.client.OptionInstance<?> option) {
        return ModernHooks.gamma(option, option.get());
    }
    //?} else {
    /^@Redirect(method = "updateLightTexture", require = 0, at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;gamma:D"))
    private double albedo$gamma(net.minecraft.client.Options options) {
        return ModernHooks.gamma(options.gamma);
    }
    ^///?}
}
*///?}
