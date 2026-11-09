package dev.albedo.legacy.mixin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import dev.albedo.legacy.LegacyHooks;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

/** The type of a downloaded texture (skin or cape), which the texture location of 1.8.9 does not tell. */
@Mixin(SkinManager.class)
public abstract class SkinManagerMixin implements LegacyHooks.SkinCache {
    @Shadow
    @Final
    private File skinCacheDir;

    private static final String LOAD_SKIN = "loadSkin(Lcom/mojang/authlib/minecraft/MinecraftProfileTexture;"
            + "Lcom/mojang/authlib/minecraft/MinecraftProfileTexture$Type;"
            + "Lnet/minecraft/client/resources/SkinManager$SkinAvailableCallback;)Lnet/minecraft/util/ResourceLocation;";

    @Inject(method = LOAD_SKIN, at = @At("HEAD"))
    private void albedo$loadSkin(MinecraftProfileTexture profileTexture, MinecraftProfileTexture.Type type,
                                 SkinManager.SkinAvailableCallback callback, CallbackInfoReturnable<ResourceLocation> cir) {
        LegacyHooks.skinLoading(type.name());
    }

    @Inject(method = LOAD_SKIN, at = @At("RETURN"))
    private void albedo$skinLoaded(MinecraftProfileTexture profileTexture, MinecraftProfileTexture.Type type,
                                   SkinManager.SkinAvailableCallback callback, CallbackInfoReturnable<ResourceLocation> cir) {
        LegacyHooks.skinLoading(null);
    }

    @Override
    public File albedo$skinCacheDir() {
        return skinCacheDir;
    }
}
