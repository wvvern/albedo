package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** The steps of a resource reload, for the progress bar of a reload that Albedo starts (ReloadScreen). */
@Mixin(SimpleReloadableResourceManager.class)
public abstract class ReloadListenersMixin {
    @Shadow
    @Final
    private List<IResourceManagerReloadListener> reloadListeners;

    @Inject(method = "notifyReloadListeners", at = @At("HEAD"))
    private void albedo$begin(CallbackInfo ci) {
        LegacyHooks.reloadBegin(reloadListeners.size());
    }

    @Inject(method = "notifyReloadListeners", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/IResourceManagerReloadListener;onResourceManagerReload(Lnet/minecraft/client/resources/IResourceManager;)V"))
    private void albedo$step(CallbackInfo ci) {
        LegacyHooks.RELOAD_STEP.run();
        LegacyHooks.reloadStep();
    }
}
