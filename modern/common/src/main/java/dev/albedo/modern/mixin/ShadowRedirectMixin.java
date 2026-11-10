package dev.albedo.modern.mixin;

//? if <1.20.5 {
/*import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Entity shadows off with plain Mixin, because Forge has no MixinExtras before 1.20.5: the read of the "entity shadows"
// option goes through the hook, with require = 0 so that a mod that redirects the same read does not stop the game.
@Mixin(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class)
public abstract class ShadowRedirectMixin {
    //? if >=1.19 {
    @Redirect(method = "render", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object albedo$shadows(net.minecraft.client.OptionInstance<?> option) {
        return ModernHooks.entityShadows(option, option.get());
    }
    //?} else {
    /^@Redirect(method = "render", require = 0, at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;entityShadows:Z"))
    private boolean albedo$shadows(net.minecraft.client.Options options) {
        return ModernHooks.entityShadows(options.entityShadows);
    }
    ^///?}
}
*///?}
