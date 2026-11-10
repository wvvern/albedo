package dev.albedo.legacy.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Gives the sprites of the block atlas, with their places in it, to the Chroma pass. */
@Mixin(TextureMap.class)
public interface TextureMapAccessor {
    @Accessor("mapUploadedSprites")
    Map<String, TextureAtlasSprite> albedo$sprites();
}
