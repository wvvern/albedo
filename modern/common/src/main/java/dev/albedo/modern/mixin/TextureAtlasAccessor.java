package dev.albedo.modern.mixin;

import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.11 {
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
//?}

/** Replaces the animation of repainted sprites, and is empty before 1.21.11 because there the sprites upload each frame. */
@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
    //? if >=1.21.11 {
    @Accessor("sprites")
    List<TextureAtlasSprite> albedo$sprites();

    @Accessor("animatedTexturesStates")
    List<SpriteContents.AnimationState> albedo$animationStates();

    @Accessor("animatedTexturesStates")
    void albedo$setAnimationStates(List<SpriteContents.AnimationState> states);

    @Accessor("mipLevelCount")
    int albedo$mipLevelCount();

    @Invoker("uploadAnimationFrames")
    void albedo$uploadAnimationFrames();
    //?}
}
