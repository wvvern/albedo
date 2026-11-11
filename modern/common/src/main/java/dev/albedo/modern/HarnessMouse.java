package dev.albedo.modern;

import com.mojang.blaze3d.platform.Window;
import dev.albedo.modern.mixin.MouseHandlerInvoker;
import net.minecraft.client.Minecraft;

/** Development-only: mouse input for the test harness through the game's own path to the screen. */
final class HarnessMouse {
    private HarnessMouse() {}

    /** Calls the window callbacks of the mouse handler for a move to a GUI pixel and a press or release of the left button. */
    static void leftButton(int guiX, int guiY, boolean down) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        MouseHandlerInvoker mouse = (MouseHandlerInvoker) mc.mouseHandler;
        // The center of the GUI pixel, in window pixels, which the handler scales back to GUI pixels.
        double x = (guiX + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth();
        double y = (guiY + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight();
        int action = down ? KeyCodes.PRESS : KeyCodes.RELEASE;
        long handle = HarnessWindow.handle();
        //? if >=26.3 {
        mouse.albedo$onMove(handle, x, y, 0, 0);
        //?} else {
        /*mouse.albedo$onMove(handle, x, y);
        *///?}
        //? if >=1.21.9 {
        mouse.albedo$onButton(handle, new net.minecraft.client.input.MouseButtonInfo(KeyCodes.MOUSE_BUTTON_LEFT, 0), action);
        //?} else {
        /*mouse.albedo$onPress(handle, KeyCodes.MOUSE_BUTTON_LEFT, action, 0);
        *///?}
    }
}
