package dev.albedo.legacy;

import net.minecraft.client.resources.data.AnimationMetadataSection;

/** The animation state of an atlas sprite, which TextureAtlasSpriteMixin implements. */
public interface SpriteAnimation {
    int albedo$frameCounter();

    AnimationMetadataSection albedo$animation();
}
