package dev.albedo.core.texture;

import dev.albedo.core.Category;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.ConfigFixtures;
import dev.albedo.core.config.Rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Images, policies and uploads that more than one texture test uses. */
final class TextureFixtures {
    static final String PARTICLES = "minecraft:textures/particle/particles.png";
    static final int GRAY = 0xFF808080, WHITE = 0xFFFFFFFF;

    private TextureFixtures() {
    }

    static PixelBuffer.Heap filled(int w, int h, int argb) {
        int[] px = new int[w * h];
        Arrays.fill(px, argb);
        return new PixelBuffer.Heap(w, h, px);
    }

    /** A sheet with one opaque color for each cell of a 16 x 16 grid, so that each cell has its own average. */
    static PixelBuffer.Heap cells(int width, int height) {
        PixelBuffer.Heap img = new PixelBuffer.Heap(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) img.setArgb(x, y, cellColor(x * 16 / width, y * 16 / height));
        }
        return img;
    }

    static int cellColor(int col, int row) {
        return 0xFF000000 | (col * 16 << 16) | (row * 16 << 8) | 0x40;
    }

    /** The default profile with one category set to {@code look} and {@code groups}. */
    static Policy policy(Category category, Appearance look, Rule... groups) {
        return Policy.of(ConfigFixtures.with(category, new CategorySettings(look, false, Arrays.asList(groups))));
    }

    static Policy particles(Appearance look, Rule... groups) {
        return policy(Category.PARTICLES, look, groups);
    }

    static Rule group(String pattern, Appearance look) {
        return new Rule(pattern, Collections.singletonList(pattern), look, false);
    }

    /** Paints with the Chroma hue on the CPU. */
    static boolean paint(Layout layout, List<Appearance> looks, PixelBuffer img) {
        return layout.paint(looks, img, true);
    }

    static double lightness(int argb) {
        return Recolor.lab(argb)[0];
    }

    /** Records each upload; can act as a texture that is gone or broken. */
    static final class FakeUpload implements TextureStore.Upload {
        final List<int[]> uploads = new ArrayList<int[]>();
        boolean gone;
        boolean broken;

        @Override
        public boolean upload(PixelBuffer pixels) throws Exception {
            if (broken) throw new IllegalStateException("broken");
            if (gone) return false;
            uploads.add(((PixelBuffer.Heap) pixels).argb().clone());
            return true;
        }
    }

    /** An upload of an atlas sprite whose hue a GPU pass adds while {@link #gpu} is true. */
    static final class GpuUpload implements TextureStore.Upload {
        final FakeUpload uploads = new FakeUpload();
        boolean gpu = true;

        @Override
        public boolean upload(PixelBuffer pixels) throws Exception {
            return uploads.upload(pixels);
        }

        @Override
        public boolean gpuHue() {
            return gpu;
        }
    }
}
