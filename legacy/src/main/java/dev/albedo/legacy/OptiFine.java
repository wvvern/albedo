package dev.albedo.legacy;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * The OptiFine members that a repaint needs, found by reflection under OptiFine names that do not change with the
 * mappings, and null with no effect when OptiFine is not there.
 */
final class OptiFine {
    private static final Method IS_SHADERS = method(type("Config"), "isShaders");
    /** A copy of the sprite in its own GL texture, with "Multitexture" on (anisotropic filtering, antialiasing). */
    private static final Field SPRITE_SINGLE = field(TextureAtlasSprite.class, "spriteSingle");
    private static final Field SPRITE_TEXTURE = field(TextureAtlasSprite.class, "glSpriteTextureId");
    private static final Method BIND_SPRITE_TEXTURE = method(TextureAtlasSprite.class, "bindSpriteTexture");

    private OptiFine() {}

    /** True with a shader pack: OptiFine then uploads textures with its own code (normal and specular maps). */
    static boolean shaders() {
        if (IS_SHADERS == null) return false;
        try {
            return (Boolean) IS_SHADERS.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true; // unknown: the caller falls back to a resource reload
        }
    }

    /** @return the single copy of the sprite, or null */
    static TextureAtlasSprite spriteSingle(TextureAtlasSprite sprite) throws IllegalAccessException {
        return SPRITE_SINGLE == null ? null : (TextureAtlasSprite) SPRITE_SINGLE.get(sprite);
    }

    /** Binds the GL texture of the single copy. @return false when the sprite has no such texture */
    static boolean bindSpriteTexture(TextureAtlasSprite sprite) throws ReflectiveOperationException {
        if (SPRITE_TEXTURE == null || BIND_SPRITE_TEXTURE == null || SPRITE_TEXTURE.getInt(sprite) < 0) return false;
        BIND_SPRITE_TEXTURE.invoke(sprite);
        return true;
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name, false, OptiFine.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            return owner == null ? null : owner.getField(name);
        } catch (NoSuchFieldException | RuntimeException e) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String name) {
        try {
            return owner == null ? null : owner.getMethod(name);
        } catch (NoSuchMethodException | RuntimeException e) {
            return null;
        }
    }
}
