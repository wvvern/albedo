package dev.albedo.modern;

//? if >=26.1 {
import dev.albedo.core.ui.CanvasImage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/** The canvas of the settings screen on the versions whose screens give their draws to a {@code GuiGraphicsExtractor}. */
final class ExtractorCanvas extends FontCanvas {
    private final GuiGraphicsExtractor g;

    ExtractorCanvas(GuiGraphicsExtractor g, Font font, int width, int height, CanvasImages images) {
        super(font, width, height, images);
        this.g = g;
    }

    @Override public void fill(int x0, int y0, int x1, int y1, int argb) { g.fill(x0, y0, x1, y1, argb); }
    @Override public void text(String text, int x, int y, int argb) { g.text(font, text, x, y, argb, false); }

    @Override
    public void image(String id, CanvasImage img, int x, int y, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, CanvasImages.id(images.prepare(id, img)), x, y, 0f, 0f, w, h,
                img.width(), img.height(), img.width(), img.height());
    }
}
//?}
