package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// With the test harness the LWJGL grab and the cursor move do not run, because a grab clips the cursor of the whole desktop and the harness sends its own input.
@Mixin(MouseHelper.class)
public abstract class MouseGrabMixin {
    @Redirect(method = {"grabMouseCursor", "ungrabMouseCursor"}, require = 0, at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/input/Mouse;setGrabbed(Z)V", remap = false))
    private void albedo$grab(boolean grabbed) {
        if (!LegacyHooks.skipMouseGrab()) Mouse.setGrabbed(grabbed);
    }

    @Redirect(method = "ungrabMouseCursor", require = 0, at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/input/Mouse;setCursorPosition(II)V", remap = false))
    private void albedo$center(int x, int y) {
        if (!LegacyHooks.skipMouseGrab()) Mouse.setCursorPosition(x, y);
    }
}
