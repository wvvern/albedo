package dev.albedo.modern.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The callbacks that the window sends to the mouse handler, which the dev harness calls to click as a real mouse does. */
@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
    //? if >=26.3 {
    @Invoker("onMove")
    void albedo$onMove(long handle, double x, double y, double dx, double dy);
    //?} else {
    /*@Invoker("onMove")
    void albedo$onMove(long handle, double x, double y);
    *///?}

    //? if >=1.21.9 {
    @Invoker("onButton")
    void albedo$onButton(long handle, net.minecraft.client.input.MouseButtonInfo button, int action);
    //?} else {
    /*@Invoker("onPress")
    void albedo$onPress(long handle, int button, int action, int modifiers);
    *///?}
}
