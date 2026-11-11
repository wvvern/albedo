package dev.albedo.modern;

//? if <1.20 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.albedo.core.ui.Canvas;
import dev.albedo.core.ui.CanvasImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;

// The canvas of the settings screen on the versions whose screens draw with a PoseStack.
final class PoseStackCanvas implements Canvas {
    private final PoseStack pose;
    private final Font font;
    private final int width;
    private final int height;
    private final CanvasImages images;

    PoseStackCanvas(PoseStack pose, Font font, int width, int height, CanvasImages images) {
        this.pose = pose;
        this.font = font;
        this.width = width;
        this.height = height;
        this.images = images;
    }

    @Override public int width() { return width; }
    @Override public int height() { return height; }
    @Override public void fill(int x0, int y0, int x1, int y1, int argb) { GuiComponent.fill(pose, x0, y0, x1, y1, argb); }
    @Override public void text(String text, int x, int y, int argb) { font.draw(pose, text, x, y, argb); }
    @Override public int textWidth(String text) { return font.width(text); }

    @Override
    public void image(String id, CanvasImage img, int x, int y, int w, int h) {
        texture(pose, CanvasImages.id(images.prepare(id, img)), x, y, w, h, img.width(), img.height());
    }

    // Draws all of a texture into a box of w by h pixels.
    static void texture(PoseStack pose, net.minecraft.resources.ResourceLocation texture, int x, int y, int w, int h, int textureWidth, int textureHeight) {
        //? if >=1.17 {
        RenderSystem.setShaderTexture(0, texture);
        //?} else {
        /^Minecraft.getInstance().getTextureManager().bind(texture);
        ^///?}
        GuiComponent.blit(pose, x, y, w, h, 0f, 0f, textureWidth, textureHeight, textureWidth, textureHeight);
    }
}
*///?}
