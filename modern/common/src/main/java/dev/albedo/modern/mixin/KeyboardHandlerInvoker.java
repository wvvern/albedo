package dev.albedo.modern.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The callbacks that the window sends to the keyboard handler, which the dev harness calls to type as a real keyboard does. */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerInvoker {
    //? if >=1.21.9 {
    @Invoker("keyPress")
    void albedo$keyPress(long handle, int action, net.minecraft.client.input.KeyEvent event);
    //?} else {
    /*@Invoker("keyPress")
    void albedo$keyPress(long handle, int key, int scancode, int action, int modifiers);
    *///?}

    //? if >=26.1 {
    @Invoker("textInput")
    void albedo$textInput(long handle, String text);
    //?} elif >=1.21.9 {
    /*@Invoker("charTyped")
    void albedo$charTyped(long handle, net.minecraft.client.input.CharacterEvent event);
    *///?} else {
    /*@Invoker("charTyped")
    void albedo$charTyped(long handle, int codepoint, int modifiers);
    *///?}
}
