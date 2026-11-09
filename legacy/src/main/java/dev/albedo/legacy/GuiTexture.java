package dev.albedo.legacy;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** Development-only: shows one texture large on a gray square, so that a harness screenshot shows a skin. */
final class GuiTexture extends GuiScreen {
    private static final int SCALE = 4;
    private static final int SIZE = 64;

    private final ResourceLocation texture;

    GuiTexture(ResourceLocation texture) {
        this.texture = texture;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int x = 8, y = 8, size = SIZE * SCALE;
        Gui.drawRect(x - 4, y - 4, x + size + 4, y + size + 4, 0xFF808080);
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(texture);
        Gui.drawScaledCustomSizeModalRect(x, y, 0f, 0f, SIZE, SIZE, size, size, SIZE, SIZE);
    }
}
