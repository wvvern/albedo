package dev.albedo.modern;

import com.mojang.blaze3d.platform.InputConstants;
import dev.albedo.core.Albedo;
import dev.albedo.core.ui.Canvas;
import dev.albedo.core.ui.SettingsEditor;
import dev.albedo.core.ui.UiKey;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Hosts the version-agnostic {@link SettingsEditor}: forwards input and draws it through a {@link Canvas}.
 * Everything version-specific about the settings screen is in this file.
 */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final SettingsEditor editor;

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
        draw(new GraphicsCanvas(g), mouseX, mouseY);
    }

    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // the editor draws its own backdrop and keeps the world visible behind it
    }
    //?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        draw(new GraphicsCanvas(g), mouseX, mouseY);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the editor draws its own backdrop and keeps the world visible behind it
    }
    *///?}

    private void draw(Canvas canvas, int mouseX, int mouseY) {
        editor.render(canvas, mouseX, mouseY);
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
        editor.mousePressed(event.x(), event.y(), event.button());
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        editor.mouseReleased(event.x(), event.y(), event.button());
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
        editor.mousePressed(x, y, button);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        editor.mouseReleased(x, y, button);
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

    /** The drawing port, over this version's GUI graphics class. */
    private final class GraphicsCanvas implements Canvas {
        //? if >=26.1 {
        private final net.minecraft.client.gui.GuiGraphicsExtractor g;

        GraphicsCanvas(net.minecraft.client.gui.GuiGraphicsExtractor g) {
            this.g = g;
        }

        @Override
        public void text(String text, int x, int y, int argb) {
            g.text(font, text, x, y, argb, false);
        }
        //?} else {
        /*private final net.minecraft.client.gui.GuiGraphics g;

        GraphicsCanvas(net.minecraft.client.gui.GuiGraphics g) {
            this.g = g;
        }

        @Override
        public void text(String text, int x, int y, int argb) {
            g.drawString(font, text, x, y, argb, false);
        }
        *///?}

        @Override public int width() { return SettingsScreen.this.width; }
        @Override public int height() { return SettingsScreen.this.height; }
        @Override public void fill(int x0, int y0, int x1, int y1, int argb) { g.fill(x0, y0, x1, y1, argb); }
        @Override public int textWidth(String text) { return font.width(text); }
    }
}
