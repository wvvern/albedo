package dev.albedo.modern;

import com.mojang.blaze3d.platform.InputConstants;
import dev.albedo.core.Albedo;
import dev.albedo.core.ui.Canvas;
import dev.albedo.core.ui.SettingsEditor;
import dev.albedo.core.ui.UiKey;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Hosts the version-agnostic {@link SettingsEditor}: the life cycle and the input of the settings screen, which draws
 * through the {@link Canvas} of the version ({@code ExtractorCanvas} or {@code GraphicsCanvas}).
 */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final SettingsEditor editor;
    private final CanvasImages images = new CanvasImages();

    public SettingsScreen(Screen parent) {
        super(Component.literal(Albedo.NAME));
        this.parent = parent;
        ModernHooks.bootstrap();
        this.editor = new SettingsEditor(Albedo.get());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ModernPlatform.setScreen(parent);
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        editor.render(new ExtractorCanvas(g, font, width, height, images), mouseX, mouseY);
    }

    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // the editor draws its own backdrop and keeps the world visible behind it
    }
    //?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        editor.render(new GraphicsCanvas(g, font, width, height, images), mouseX, mouseY);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the editor draws its own backdrop and keeps the world visible behind it
    }
    *///?}

    /** Closes the screen in the tick and not in the frame, because the GUI draws this frame's images after the frame's calls. */
    @Override
    public void tick() {
        if (editor.isClosed()) onClose();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        editor.mouseScrolled(vertical);
        return true;
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) editor.mousePressed();
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) editor.mouseReleased();
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return key(event.key(), event.hasControlDown());
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        for (char c : Character.toChars(event.codepoint())) editor.charTyped(c);
        return true;
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) editor.mousePressed();
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) editor.mouseReleased();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return key(keyCode, Screen.hasControlDown());
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        editor.charTyped(c);
        return true;
    }
    *///?}

    private boolean key(int code, boolean ctrl) {
        UiKey key = code == InputConstants.KEY_BACKSPACE ? UiKey.BACKSPACE
                : code == InputConstants.KEY_DELETE ? UiKey.DELETE
                : code == InputConstants.KEY_LEFT ? UiKey.LEFT
                : code == InputConstants.KEY_RIGHT ? UiKey.RIGHT
                : code == InputConstants.KEY_UP ? UiKey.UP
                : code == InputConstants.KEY_DOWN ? UiKey.DOWN
                : code == InputConstants.KEY_HOME ? UiKey.HOME
                : code == InputConstants.KEY_END ? UiKey.END
                : code == InputConstants.KEY_RETURN || code == InputConstants.KEY_NUMPADENTER ? UiKey.ENTER
                : code == InputConstants.KEY_ESCAPE ? UiKey.ESCAPE
                : code == InputConstants.KEY_TAB ? UiKey.TAB
                : code == InputConstants.KEY_A ? UiKey.A
                : code == InputConstants.KEY_V ? UiKey.V
                : null;
        if (key != null) editor.keyPressed(key, ctrl);
        return true;
    }

    @Override
    public void removed() {
        images.close();
        super.removed();
    }
}
