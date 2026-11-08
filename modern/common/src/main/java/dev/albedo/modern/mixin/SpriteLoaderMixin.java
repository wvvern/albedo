package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Every atlas (blocks, items, particles, celestials, paintings...) is stitched here, after all sprites are loaded
 * and before mipmaps exist. The atlas id tells us which folder prefix-less sprite names came from.
 */
@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {
    //? if >=1.21.11 {
    @Shadow @Final private net.minecraft.resources.Identifier location;
    //?} else {
    /*@Shadow @Final private net.minecraft.resources.ResourceLocation location;
    *///?}

    @Inject(method = "stitch", at = @At("HEAD"))
    private void albedo$flatten(List<SpriteContents> sprites, int mipLevel, Executor executor,
                                     CallbackInfoReturnable<SpriteLoader.Preparations> cir) {
        String atlas = String.valueOf(location);
        for (SpriteContents sprite : sprites) ModernHooks.processSprite(atlas, sprite);
    }
}
