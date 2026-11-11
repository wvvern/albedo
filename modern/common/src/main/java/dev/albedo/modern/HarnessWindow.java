package dev.albedo.modern;

import net.minecraft.client.Minecraft;

/** Development-only: the handle of the game window for the input of the test harness. */
final class HarnessWindow {
    private HarnessWindow() {}

    /** The handle of the window in the window library of the version. */
    static long handle() {
        //? if >=1.21.9 {
        return Minecraft.getInstance().getWindow().handle();
        //?} else {
        /*return Minecraft.getInstance().getWindow().getWindow();
        *///?}
    }
}
