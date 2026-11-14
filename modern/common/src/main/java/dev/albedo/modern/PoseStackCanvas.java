package dev.albedo.modern;

//? if <1.20 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.albedo.core.ui.CanvasImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;

// The canvas of the settings screen on the versions whose screens draw with a PoseStack.
final class PoseStackCanvas extends FontCanvas {
    private final PoseStack pose;

    PoseStackCanvas(PoseStack pose, Font font, int width, int height, CanvasImages images) {
        super(font, width, height, images);
        this.pose = pose;
    }

    @Override public void fill(int x0, int y0, int x1, int y1, int argb) { GuiComponent.fill(pose, x0, y0, x1, y1, argb); }
    @Override public void text(String text, int x, int y, int argb) { font.draw(pose, text, x, y, argb); }

    // Draws all of the image into a box of w by h pixels.
    @Override
    public void image(String id, CanvasImage img, int x, int y, int w, int h) {
        net.minecraft.resources.ResourceLocation texture = CanvasImages.id(images.prepare(id, img));
        //? if >=1.17 {
        RenderSystem.setShaderTexture(0, texture);
        //?} else {
        /^Minecraft.getInstance().getTextureManager().bind(texture);
        ^///?}
        GuiComponent.blit(pose, x, y, w, h, 0f, 0f, img.width(), img.height(), img.width(), img.height());
    }
}
*///?}
