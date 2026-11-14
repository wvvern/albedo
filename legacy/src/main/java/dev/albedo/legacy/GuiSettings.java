package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.ui.Canvas;
import dev.albedo.core.ui.CanvasImage;
import dev.albedo.core.ui.SettingsEditor;
import dev.albedo.core.ui.UiKey;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Hosts the version-agnostic {@link SettingsEditor} on the legacy versions. */
public final class GuiSettings extends GuiScreen implements Canvas {
    /** The LWJGL 2 number of the left mouse button. */
    private static final int LEFT_BUTTON = 0;
    private final GuiScreen parent;
    private final SettingsEditor editor;
    /** One texture for each image id and size, kept until the screen closes. */
    private final Map<String, Image> images = new HashMap<String, Image>();

    public GuiSettings(GuiScreen parent) {
        this.parent = parent;
        LegacyHooks.bootstrap();
        this.editor = new SettingsEditor(Albedo.get());
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        for (Image image : images.values()) mc.getTextureManager().deleteTexture(image.location);
        images.clear();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        editor.render(this, mouseX, mouseY);
    }

    /** Closes the screen in the tick and not in the frame, so that the textures stay until the frame is complete. */
    @Override
    public void updateScreen() {
        if (editor.isClosed()) mc.displayGuiScreen(parent);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (button == LEFT_BUTTON) editor.mousePressed();
    }

    @Override
    protected void mouseReleased(int x, int y, int button) {
        if (button == LEFT_BUTTON) editor.mouseReleased();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) editor.mouseScrolled(wheel);
    }

    /** LWJGL 2 gives the key and its character in one event, where the character of a key such as Backspace is a control character. */
    @Override
    protected void keyTyped(char c, int code) {
        UiKey key = isKeyComboCtrlA(code) ? UiKey.SELECT_ALL : isKeyComboCtrlV(code) ? UiKey.PASTE : uiKey(code);
        if (key != null) editor.keyPressed(key);
        editor.charTyped(c);
    }

    private static UiKey uiKey(int code) {
        switch (code) {
            case Keyboard.KEY_BACK: return UiKey.BACKSPACE;
            case Keyboard.KEY_DELETE: return UiKey.DELETE;
            case Keyboard.KEY_LEFT: return UiKey.LEFT;
            case Keyboard.KEY_RIGHT: return UiKey.RIGHT;
            case Keyboard.KEY_UP: return UiKey.UP;
            case Keyboard.KEY_DOWN: return UiKey.DOWN;
            case Keyboard.KEY_HOME: return UiKey.HOME;
            case Keyboard.KEY_END: return UiKey.END;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER: return UiKey.ENTER;
            case Keyboard.KEY_ESCAPE: return UiKey.ESCAPE;
            case Keyboard.KEY_TAB: return UiKey.TAB;
            default: return null;
        }
    }

    @Override public int width() { return width; }
    @Override public int height() { return height; }
    @Override public void fill(int x0, int y0, int x1, int y1, int argb) { Gui.drawRect(x0, y0, x1, y1, argb); }
    @Override public void text(String text, int x, int y, int argb) { Game.font().drawString(text, x, y, argb); }
    @Override public int textWidth(String text) { return Game.font().getStringWidth(text); }

    @Override
    public void image(String id, CanvasImage img, int x, int y, int w, int h) {
        String key = (id + "_" + img.width() + "x" + img.height()).toLowerCase(Locale.ROOT);
        Image image = images.get(key);
        if (image == null) {
            DynamicTexture texture = new DynamicTexture(img.width(), img.height());
            image = new Image(texture, mc.getTextureManager().getDynamicTextureLocation("albedo_" + key, texture));
            images.put(key, image);
            upload(image, img);
        } else if (image.serial != img.serial()) {
            upload(image, img);
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(image.location);
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, img.width(), img.height(), w, h, img.width(), img.height());
    }

    private static void upload(Image image, CanvasImage img) {
        int[] data = image.texture.getTextureData();
        int[] argb = img.argb();
        for (int i = 0; i < argb.length; i++) data[i] = argb[i] | 0xFF000000;
        image.texture.updateDynamicTexture();
        image.serial = img.serial();
    }

    private static final class Image {
        final DynamicTexture texture;
        final ResourceLocation location;
        long serial;

        Image(DynamicTexture texture, ResourceLocation location) {
            this.texture = texture;
            this.location = location;
        }
    }
}
