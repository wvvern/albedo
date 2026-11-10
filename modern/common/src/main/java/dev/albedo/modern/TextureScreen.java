package dev.albedo.modern;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Development-only: shows one texture large on a gray square, so that a harness screenshot shows a skin. */
final class TextureScreen extends Screen {
    private static final int SCALE = 4;
    private static final int SIZE = 64;

    //? if >=1.21.11 {
    private final net.minecraft.resources.Identifier texture;
    //?} else {
    /*private final net.minecraft.resources.ResourceLocation texture;
    *///?}

    /** @param id the texture, for example "minecraft:skins/<sha1>" */
    TextureScreen(String id) {
        super(Component.literal("Texture"));
        this.texture = HarnessSkins.id(id);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = 8, y = 8, size = SIZE * SCALE;
        g.fill(x - 4, y - 4, x + size + 4, y + size + 4, 0xFF808080);
        g.blit(texture, x, y, x + size, y + size, 0f, 1f, 0f, 1f);
    }

    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // the world stays visible behind the texture
    }
    //?} elif >=1.21.6 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = 8, y = 8, size = SIZE * SCALE;
        g.fill(x - 4, y - 4, x + size + 4, y + size + 4, 0xFF808080);
        g.blit(texture, x, y, x + size, y + size, 0f, 1f, 0f, 1f);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the world stays visible behind the texture
    }
    *///?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = 8, y = 8, size = SIZE * SCALE;
        g.fill(x - 4, y - 4, x + size + 4, y + size + 4, 0xFF808080);
        g.blit(texture, x, y, size, size, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the world stays visible behind the texture
    }
    *///?}
}
