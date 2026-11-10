package dev.albedo.modern;

import com.mojang.blaze3d.platform.InputConstants;
import dev.albedo.core.Keys;
import dev.albedo.modern.mixin.KeyboardHandlerInvoker;
import net.minecraft.client.Minecraft;

/** Development-only: keyboard input for the test harness through the window callbacks of the keyboard handler. */
final class HarnessKeyboard {
    private HarnessKeyboard() {}

    /** Calls the keyboard handler as the key event of the window does, with the codes of the key in this version. */
    static void key(Keys.Key key, boolean down, boolean ctrl) {
        KeyboardHandlerInvoker keyboard = (KeyboardHandlerInvoker) Minecraft.getInstance().keyboardHandler;
        long handle = HarnessWindow.handle();
        int code = InputConstants.getKey(key.minecraftName).getValue();
        int action = down ? InputConstants.PRESS : InputConstants.RELEASE;
        int modifiers = ctrl ? InputConstants.MOD_CONTROL : 0;
        //? if >=26.3 {
        // The SDL event gives the scancode and the key code of the keyboard layout.
        int keycode = org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyFromScancode(code, (short) 0, false);
        keyboard.albedo$keyPress(handle, action, new net.minecraft.client.input.KeyEvent(code, keycode, modifiers));
        //?} elif >=1.21.9 {
        /*int scancode = org.lwjgl.glfw.GLFW.glfwGetKeyScancode(code);
        keyboard.albedo$keyPress(handle, action, new net.minecraft.client.input.KeyEvent(code, scancode, modifiers));
        *///?} else {
        /*keyboard.albedo$keyPress(handle, code, org.lwjgl.glfw.GLFW.glfwGetKeyScancode(code), action, modifiers);
        *///?}
    }

    /** Calls the keyboard handler as the text event of the window does, and returns false when SDL has no text input started. */
    static boolean character(char c) {
        KeyboardHandlerInvoker keyboard = (KeyboardHandlerInvoker) Minecraft.getInstance().keyboardHandler;
        long handle = HarnessWindow.handle();
        //? if >=26.3 {
        if (!org.lwjgl.sdl.SDLKeyboard.SDL_TextInputActive(handle)) return false;
        keyboard.albedo$textInput(handle, String.valueOf(c));
        //?} elif >=1.21.9 {
        /*keyboard.albedo$charTyped(handle, characterEvent(c));
        *///?} else {
        /*keyboard.albedo$charTyped(handle, c, 0);
        *///?}
        return true;
    }

    //? if >=1.21.9 && <26.1 {
    /*private static net.minecraft.client.input.CharacterEvent characterEvent(char c) {
        return new net.minecraft.client.input.CharacterEvent(c, 0);
    }
    *///?} elif >=26.1 && <26.3 {
    /*private static net.minecraft.client.input.CharacterEvent characterEvent(char c) {
        return new net.minecraft.client.input.CharacterEvent(c);
    }
    *///?}
}
