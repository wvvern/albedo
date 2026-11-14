package dev.albedo.modern;

import dev.albedo.core.Albedo;
import dev.albedo.core.ui.Canvas;
import dev.albedo.core.ui.SettingsEditor;
import dev.albedo.core.ui.UiKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Hosts the {@link SettingsEditor} (screen life cycle and input), which draws through the {@link Canvas} of the version. */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final SettingsEditor editor;
    private final CanvasImages images = new CanvasImages();

    public SettingsScreen(Screen parent) {
        super(ModernPlatform.text(Albedo.NAME));
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
        textInput();
    }

    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // the editor draws its own backdrop and keeps the world visible behind it
    }
    //?} elif >=1.20 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        editor.render(new GraphicsCanvas(g, font, width, height, images), mouseX, mouseY);
        textInput();
    }
    *///?} else {
    /*@Override
    public void render(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float partialTick) {
        editor.render(new PoseStackCanvas(pose, font, width, height, images), mouseX, mouseY);
    }
    *///?}

    //? if >=1.20.2 && <26.1 {
    /*@Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the editor draws its own backdrop and keeps the world visible behind it
    }
    *///?}

    /** Asks an SDL window for text input while a field has the focus, because SDL sends typed text only then, as for an edit box. */
    private void textInput() {
        //? if >=26.3 {
        com.mojang.blaze3d.platform.TextInputManager input = Minecraft.getInstance().textInputManager();
        int[] cursor = editor.textCursor();
        if (cursor == null) {
            input.stopTextInput(this);
            return;
        }
        input.setTextInputArea(cursor[0], cursor[1], cursor[2], cursor[3]);
        input.startTextInput(this);
        //?}
    }

    /** Closes the screen in the tick and not in the frame, because the GUI draws this frame's images after the frame's calls. */
    @Override
    public void tick() {
        if (editor.isClosed()) onClose();
    }

    //? if >=1.20.2 {
    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        editor.mouseScrolled(vertical);
        return true;
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double x, double y, double vertical) {
        editor.mouseScrolled(vertical);
        return true;
    }
    *///?}

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == KeyCodes.MOUSE_BUTTON_LEFT) editor.mousePressed();
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == KeyCodes.MOUSE_BUTTON_LEFT) editor.mouseReleased();
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        key(event.isSelectAll() ? UiKey.SELECT_ALL : event.isPaste() ? UiKey.PASTE : uiKey(event.key()));
        return true;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        for (char c : Character.toChars(event.codepoint())) editor.charTyped(c);
        return true;
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == KeyCodes.MOUSE_BUTTON_LEFT) editor.mousePressed();
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == KeyCodes.MOUSE_BUTTON_LEFT) editor.mouseReleased();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // A shortcut is Command on a Mac, else Control, with no Shift or Alt, read from the modifiers of the event.
        int shortcut = Minecraft.ON_OSX ? org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER : org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
        boolean plain = (modifiers & shortcut) != 0 && (modifiers & (org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT | org.lwjgl.glfw.GLFW.GLFW_MOD_ALT)) == 0;
        key(plain && keyCode == KeyCodes.KEY_A ? UiKey.SELECT_ALL : plain && keyCode == KeyCodes.KEY_V ? UiKey.PASTE : uiKey(keyCode));
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        editor.charTyped(c);
        return true;
    }
    *///?}

    private void key(UiKey key) {
        if (key != null) editor.keyPressed(key);
    }

    /** The editor key of a key code of this version (a GLFW key, or an SDL scancode on 26.x), or null. */
    private static UiKey uiKey(int code) {
        switch (code) {
            case KeyCodes.KEY_BACKSPACE: return UiKey.BACKSPACE;
            case KeyCodes.KEY_DELETE: return UiKey.DELETE;
            case KeyCodes.KEY_LEFT: return UiKey.LEFT;
            case KeyCodes.KEY_RIGHT: return UiKey.RIGHT;
            case KeyCodes.KEY_UP: return UiKey.UP;
            case KeyCodes.KEY_DOWN: return UiKey.DOWN;
            case KeyCodes.KEY_HOME: return UiKey.HOME;
            case KeyCodes.KEY_END: return UiKey.END;
            case KeyCodes.KEY_RETURN: case KeyCodes.KEY_NUMPADENTER: return UiKey.ENTER;
            case KeyCodes.KEY_ESCAPE: return UiKey.ESCAPE;
            case KeyCodes.KEY_TAB: return UiKey.TAB;
            default: return null;
        }
    }

    @Override
    public void removed() {
        //? if >=26.3 {
        Minecraft.getInstance().textInputManager().stopTextInput(this);
        //?}
        images.close();
        super.removed();
    }
}
