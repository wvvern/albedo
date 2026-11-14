package dev.albedo.modern;

import dev.albedo.core.Albedo;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/** The resource ids of the mod, so that the callers name no id class of one version. */
final class ModIds {
    private ModIds() {}

    //? if >=1.21.11 {
    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Albedo.MOD_ID, path);
    }
    //?} elif >=1.21 {
    /*static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Albedo.MOD_ID, path);
    }
    *///?} else {
    /*static ResourceLocation id(String path) {
        return new ResourceLocation(Albedo.MOD_ID, path);
    }
    *///?}
}
