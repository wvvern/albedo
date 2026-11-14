package dev.albedo.modern.harness;

import net.minecraft.client.Minecraft;

/** Development-only: the handle of the game window for the input of the test harness. */
public final class HarnessWindow {
    private HarnessWindow() {}

    /** The handle of the window in the window library of the version. */
    public static long handle() {
        //? if >=1.21.9 {
        return Minecraft.getInstance().getWindow().handle();
        //?} else {
        /*return Minecraft.getInstance().getWindow().getWindow();
        *///?}
    }
}
