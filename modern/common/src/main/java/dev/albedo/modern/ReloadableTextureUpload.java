package dev.albedo.modern;

//? if >=1.21.4 && <1.21.11 {
/*import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.texture.TextureStore;
import dev.albedo.modern.mixin.TextureUploadInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ReloadableTexture;

// Puts the pixels of a stand-alone texture back into its GPU texture, where the texture class has a resource id.
final class ReloadableTextureUpload {
    private ReloadableTextureUpload() {}

    static TextureStore.Upload texture(Object location) {
        return pixels -> {
            AbstractTexture registered = TextureUploads.registered(location);
            if (!(registered instanceof ReloadableTexture) || !((ReloadableTexture) registered).resourceId().equals(location)) return false;
            try (NativeImage image = new NativeImage(pixels.width(), pixels.height(), false)) {
                TextureUploads.write(pixels, image);
                //? if >=1.21.5 {
                // A GPU texture with one level, which keeps the filters of the load.
                com.mojang.blaze3d.textures.GpuTexture gpu = registered.getTexture();
                if (gpu.getWidth(0) != pixels.width() || gpu.getHeight(0) != pixels.height()) return false;
                com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().writeToTexture(gpu, image);
                //?} else {
                /^// Loads again with the blur and clamp of the metadata file, as TextureContents.load reads them.
                boolean[] filters = filters(location);
                ((TextureUploadInvoker) registered).albedo$doLoad(image, filters[0], filters[1]);
                ^///?}
            }
            return true;
        };
    }

    //? if <1.21.5 {
    /^// The blur and the clamp flag of the metadata file of a texture.
    private static boolean[] filters(Object location) {
        boolean blur = false;
        boolean clamp = false;
        java.util.Optional<net.minecraft.server.packs.resources.Resource> resource =
                Minecraft.getInstance().getResourceManager().getResource((net.minecraft.resources.ResourceLocation) location);
        try {
            java.util.Optional<net.minecraft.client.resources.metadata.texture.TextureMetadataSection> section = resource.isPresent()
                    ? resource.get().metadata().getSection(net.minecraft.client.resources.metadata.texture.TextureMetadataSection.TYPE)
                    : java.util.Optional.empty();
            if (section.isPresent()) {
                blur = section.get().blur();
                clamp = section.get().clamp();
            }
        } catch (java.io.IOException e) {
            dev.albedo.core.Albedo.get().platform().warn("Cannot read the metadata of " + location, e);
        }
        return new boolean[]{blur, clamp};
    }
    ^///?}
}
*///?}
