package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyTurning;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyBakerTest {
    private static final int W = 128, H = 32;

    /** A baker that has baked the nebula, or that is still at it after 4 seconds. */
    private static SkyBaker nebula() throws InterruptedException {
        SkyBaker baker = new SkyBaker(W, H, e -> fail(e));
        baker.request(SkyPresets.NEBULA);
        for (int i = 0; i < 400 && !baker.current(); i++) Thread.sleep(10);
        return baker;
    }

    @Test
    void theBakerInParallelGivesTheSamePixels() throws InterruptedException {
        SkyBaker baker = nebula();
        assertTrue(baker.current());
        assertArrayEquals(SkyGenerator.bake(SkyPresets.NEBULA, W, H).argb(), baker.image().argb());
    }

    @Test
    void aNewRenderKeepsThePixelsWithNoBake() throws InterruptedException {
        SkyBaker baker = nebula();
        SkyImage sharp = baker.image();
        assertTrue(sharp.drawsSharp());
        baker.request(SkyPresets.NEBULA.withRender(SkyDesign.Render.FAST));
        assertTrue(baker.current(), "the image is ready at once");
        assertSame(sharp.argb(), baker.image().argb());
        assertNotEquals(sharp.serial(), baker.image().serial(), "the adapters see a new image");
        assertFalse(baker.image().drawsSharp());
        SkyDesign turning = SkyPresets.NEBULA.withTurning(SkyTurning.STILL.withAmount(50));
        baker.request(turning);
        assertTrue(baker.current(), "a new turning also keeps the pixels");
        assertSame(sharp.argb(), baker.image().argb());
        assertEquals(turning, baker.image().design());
    }
}
