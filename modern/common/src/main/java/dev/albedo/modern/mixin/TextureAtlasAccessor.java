package dev.albedo.modern.mixin;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
//? if >=1.21.11 {
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
//?}

/**
 * Gives the sprites of an atlas to the Chroma pass, and on 1.21.11 and later replaces the animation of repainted sprites,
 * because before 1.21.11 the sprites upload each frame.
 */
@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
    @Accessor("texturesByName")
    Map<?, TextureAtlasSprite> albedo$texturesByName();

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
