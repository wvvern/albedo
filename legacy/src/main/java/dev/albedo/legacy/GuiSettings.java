package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.ui.Canvas;
import dev.albedo.core.ui.SettingsEditor;
import dev.albedo.core.ui.UiKey;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Hosts the version-agnostic {@link SettingsEditor} on 1.8.9. */
public final class GuiSettings extends GuiScreen implements Canvas {
    private final GuiScreen parent;
    private final SettingsEditor editor;

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
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        editor.render(this, mouseX, mouseY);
        if (editor.isClosed()) mc.displayGuiScreen(parent);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        editor.mousePressed(button);
    }

    @Override
    protected void mouseReleased(int x, int y, int button) {
        editor.mouseReleased(button);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) editor.mouseScrolled(wheel);
    }

    @Override
    protected void keyTyped(char c, int code) {
        UiKey key = code == Keyboard.KEY_BACK ? UiKey.BACKSPACE
                : code == Keyboard.KEY_DELETE ? UiKey.DELETE
                : code == Keyboard.KEY_LEFT ? UiKey.LEFT
                : code == Keyboard.KEY_RIGHT ? UiKey.RIGHT
                : code == Keyboard.KEY_UP ? UiKey.UP
                : code == Keyboard.KEY_DOWN ? UiKey.DOWN
                : code == Keyboard.KEY_HOME ? UiKey.HOME
                : code == Keyboard.KEY_END ? UiKey.END
                : code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER ? UiKey.ENTER
                : code == Keyboard.KEY_ESCAPE ? UiKey.ESCAPE
                : code == Keyboard.KEY_TAB ? UiKey.TAB
                : code == Keyboard.KEY_A ? UiKey.A
                : code == Keyboard.KEY_V ? UiKey.V
                : null;
        boolean ctrl = isCtrlKeyDown();
        if (key != null) editor.keyPressed(key, ctrl);
        if (!ctrl && c >= ' ' && key != UiKey.ESCAPE) editor.charTyped(c);
    }

    // ---------------------------------------------------------------- Canvas

    @Override public int width() { return width; }
    @Override public int height() { return height; }
    @Override public void fill(int x0, int y0, int x1, int y1, int argb) { Gui.drawRect(x0, y0, x1, y1, argb); }
    @Override public void text(String text, int x, int y, int argb) { fontRendererObj.drawString(text, x, y, argb); }
    @Override public int textWidth(String text) { return fontRendererObj.getStringWidth(text); }
}
