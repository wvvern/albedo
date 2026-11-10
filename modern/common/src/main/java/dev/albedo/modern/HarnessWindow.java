package dev.albedo.modern;

import net.minecraft.client.Minecraft;

/** Development-only: the game window as the test harness needs it. */
final class HarnessWindow {
    private HarnessWindow() {}

    /** The handle of the window in the window library of the version. */
    static long handle() {
        //? if >=1.21.11 {
        return Minecraft.getInstance().getWindow().handle();
        //?} else {
        /*return Minecraft.getInstance().getWindow().getWindow();
        *///?}
    }

    /** Gives the frame a size with no border, so that it can be as large as the screen. */
    static void resize(int width, int height) {
        long handle = handle();
        //? if >=26.1 {
        org.lwjgl.sdl.SDLVideo.SDL_SetWindowBordered(handle, false);
        org.lwjgl.sdl.SDLVideo.SDL_SetWindowPosition(handle, 0, 0);
        org.lwjgl.sdl.SDLVideo.SDL_SetWindowSize(handle, width, height);
        //?} else {
        /*org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(handle, org.lwjgl.glfw.GLFW.GLFW_DECORATED, org.lwjgl.glfw.GLFW.GLFW_FALSE);
        org.lwjgl.glfw.GLFW.glfwSetWindowPos(handle, 0, 0);
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(handle, width, height);
        *///?}
    }
}
