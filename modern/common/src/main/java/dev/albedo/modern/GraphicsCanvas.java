package dev.albedo.modern;

//? if >=1.20 && <26.1 {
/*import dev.albedo.core.ui.Canvas;
import dev.albedo.core.ui.CanvasImage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

// The canvas of the settings screen on the versions whose screens draw with a GuiGraphics.
final class GraphicsCanvas implements Canvas {
    private final GuiGraphics g;
    private final Font font;
    private final int width;
    private final int height;
    private final CanvasImages images;

    GraphicsCanvas(GuiGraphics g, Font font, int width, int height, CanvasImages images) {
        this.g = g;
        this.font = font;
        this.width = width;
        this.height = height;
        this.images = images;
    }

    @Override public int width() { return width; }
    @Override public int height() { return height; }
    @Override public void fill(int x0, int y0, int x1, int y1, int argb) { g.fill(x0, y0, x1, y1, argb); }
    @Override public void text(String text, int x, int y, int argb) { g.drawString(font, text, x, y, argb, false); }
    @Override public int textWidth(String text) { return font.width(text); }

    // How a GUI texture draws, for a blit.
    //? if >=1.21.6 {
    static final com.mojang.blaze3d.pipeline.RenderPipeline TEXTURED = net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;
    //?} elif >=1.21.2 {
    /^static final java.util.function.Function<net.minecraft.resources.ResourceLocation, net.minecraft.client.renderer.RenderType> TEXTURED =
            net.minecraft.client.renderer.RenderType::guiTextured;
    ^///?}

    @Override
    public void image(String id, CanvasImage img, int x, int y, int w, int h) {
        var location = CanvasImages.id(images.prepare(id, img));
        //? if >=1.21.2 {
        g.blit(TEXTURED, location, x, y, 0f, 0f, w, h, img.width(), img.height(), img.width(), img.height());
        //?} else {
        /^g.blit(location, x, y, w, h, 0f, 0f, img.width(), img.height(), img.width(), img.height());
        ^///?}
    }
}
*///?}
