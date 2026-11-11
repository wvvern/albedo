package dev.albedo.modern.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.albedo.modern.ModernHooks;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// With the test harness the window calls of the mouse grab and release do not run, because a grab clips the cursor of the whole desktop and the harness sends its own mouse events.
@Mixin(MouseHandler.class)
public abstract class MouseGrabMixin {
    //? if >=26.3 {
    @Redirect(method = "grabMouse", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/InputConstants;grabMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"))
    private void albedo$grab(com.mojang.blaze3d.platform.Window window, double x, double y) {
        if (!ModernHooks.skipMouseGrab()) InputConstants.grabMouse(window, x, y);
    }

    @Redirect(method = "releaseMouse", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/InputConstants;releaseMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"))
    private void albedo$release(com.mojang.blaze3d.platform.Window window, double x, double y) {
        if (!ModernHooks.skipMouseGrab()) InputConstants.releaseMouse(window, x, y);
    }
    //?} elif >=1.21.10 {
    /*@Redirect(method = {"grabMouse", "releaseMouse"}, require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(Lcom/mojang/blaze3d/platform/Window;IDD)V"))
    private void albedo$grabOrRelease(com.mojang.blaze3d.platform.Window window, int mode, double x, double y) {
        if (!ModernHooks.skipMouseGrab()) InputConstants.grabOrReleaseMouse(window, mode, x, y);
    }
    *///?} else {
    /*@Redirect(method = {"grabMouse", "releaseMouse"}, require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(JIDD)V"))
    private void albedo$grabOrRelease(long window, int mode, double x, double y) {
        if (!ModernHooks.skipMouseGrab()) InputConstants.grabOrReleaseMouse(window, mode, x, y);
    }
    *///?}
}
