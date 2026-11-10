package dev.albedo.modern;

//? if >=26.1 {
import dev.albedo.core.ui.Canvas;
import dev.albedo.core.ui.CanvasImage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/** The canvas of the settings screen on the versions whose screens give their draws to a {@code GuiGraphicsExtractor}. */
final class ExtractorCanvas implements Canvas {
    private final GuiGraphicsExtractor g;
    private final Font font;
    private final int width;
    private final int height;
    private final CanvasImages images;

    ExtractorCanvas(GuiGraphicsExtractor g, Font font, int width, int height, CanvasImages images) {
        this.g = g;
        this.font = font;
        this.width = width;
        this.height = height;
        this.images = images;
    }

    @Override public int width() { return width; }
    @Override public int height() { return height; }
    @Override public void fill(int x0, int y0, int x1, int y1, int argb) { g.fill(x0, y0, x1, y1, argb); }
    @Override public void text(String text, int x, int y, int argb) { g.text(font, text, x, y, argb, false); }
    @Override public int textWidth(String text) { return font.width(text); }

    @Override
    public void image(String id, CanvasImage img, int x, int y, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, CanvasImages.id(images.prepare(id, img)), x, y, 0f, 0f, w, h,
                img.width(), img.height(), img.width(), img.height());
    }
}
//?}
