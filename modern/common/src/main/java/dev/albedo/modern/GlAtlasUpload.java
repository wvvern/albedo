package dev.albedo.modern;

//? if <1.21.11 {
/*import java.util.List;

// Puts repainted sprites into their atlases on the versions where each sprite uploads its own pixels, through the GL or
// from 1.21.5 through the GPU device.
final class GlAtlasUpload {
    private GlAtlasUpload() {}

    static void upload(List<TextureUploads.Pending> pending) {
        pending.parallelStream().forEach(TextureUploads.Pending::repaint);
        // Animated sprites upload their next frame from their mip levels, so they animate with the new pixels.
        for (TextureUploads.Pending p : pending) {
            //? if >=1.21.5 {
            p.sprite.uploadFirstFrame(p.atlas.getTexture());
            //?} else {
            /^p.atlas.bind();
            p.sprite.uploadFirstFrame();
            ^///?}
        }
    }
}
*///?}
