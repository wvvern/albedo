package dev.albedo.core.texture;

import dev.albedo.core.Colors;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.config.Appearance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PixelTransformerTest {

    @Test
    void transformsKeepShapeAndHide() {
        PixelBuffer.Heap img = new PixelBuffer.Heap(2, 1, new int[]{0xFF123456, 0x00ABCDEF});
        PixelTransformer.apply(Appearance.solid(0xFFFFFFFF), img);
        assertEquals(0xFFFFFFFF, img.getArgb(0, 0));
        assertEquals(0x00FFFFFF, img.getArgb(1, 0));

        PixelTransformer.apply(new Appearance(Appearance.Mode.SOLID, 0xFF808080, Appearance.Alpha.OPAQUE), img);
        assertEquals(0xFF808080, img.getArgb(1, 0));

        PixelTransformer.apply(Appearance.of(Appearance.Mode.HIDDEN), img);
        assertEquals(0, Colors.alpha(img.getArgb(0, 0)));

        assertEquals(0xFF7F7F7F, PixelTransformer.average(new PixelBuffer.Heap(2, 1, new int[]{0xFF000000, 0xFFFFFFFF})));
    }
}
