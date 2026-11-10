package dev.albedo.modern;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Development-only: loads a skin or cape file as a downloaded texture for the test harness. */
final class HarnessSkins {
    private HarnessSkins() {}

    //? if >=1.21.11 {
    static net.minecraft.resources.Identifier id(String id) {
        return net.minecraft.resources.Identifier.parse(id);
    }
    //?} elif >=1.21 {
    /*static net.minecraft.resources.ResourceLocation id(String id) {
        return net.minecraft.resources.ResourceLocation.parse(id);
    }
    *///?} else {
    /*static net.minecraft.resources.ResourceLocation id(String id) {
        return new net.minecraft.resources.ResourceLocation(id);
    }
    *///?}

    /** Registers the image as the game registers a downloaded skin or cape with this hash, and returns the id of the texture. */
    static String load(File png, String hash, boolean cape) throws IOException {
        Minecraft mc = Minecraft.getInstance();
        String folder = cape ? "capes/" : "skins/";
        // The game names a skin by the SHA-1 of its hash and keeps the file in <cache>/<first 2>/<hash>.
        var id = id("minecraft:" + folder + com.google.common.hash.Hashing.sha1().hashUnencodedChars(hash));
        File file = new File(new File(new File(png.getParentFile(), "skin-cache"), hash.substring(0, 2)), hash);
        Files.createDirectories(file.getParentFile().toPath());
        Files.copy(png.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        String url = "http://127.0.0.1:9/" + hash; // never read, because the cache file exists
        //? if >=1.21.9 {
        new net.minecraft.client.renderer.texture.SkinTextureDownloader(java.net.Proxy.NO_PROXY, mc.getTextureManager(), mc)
                .downloadAndRegisterSkin(id, file.toPath(), url, !cape);
        //?} elif >=1.21.4 {
        /*net.minecraft.client.renderer.texture.SkinTextureDownloader.downloadAndRegisterSkin(id, file.toPath(), url, !cape);
        *///?} else {
        /*mc.getTextureManager().register(id, new net.minecraft.client.renderer.texture.HttpTexture(file, url,
                id(defaultSkin()), !cape, null));
        *///?}
        return id.toString();
    }

    /** The id of the default skin of the game. */
    static String defaultSkin() {
        //? if >=1.20.2 {
        return net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture().toString();
        //?} else {
        /*return net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin().toString();
        *///?}
    }
}
