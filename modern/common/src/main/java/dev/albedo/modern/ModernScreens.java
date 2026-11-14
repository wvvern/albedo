package dev.albedo.modern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Version shims for the screens and their text. */
public final class ModernScreens {
    private ModernScreens() {}

    public static Component text(String text) {
        //? if >=1.19 {
        return Component.literal(text);
        //?} else {
        /*return new net.minecraft.network.chat.TextComponent(text);
        *///?}
    }

    public static Screen currentScreen() {
        //? if >=26.2 {
        return Minecraft.getInstance().gui.screen();
        //?} else {
        /*return Minecraft.getInstance().screen;
        *///?}
    }

    public static void setScreen(Screen screen) {
        //? if >=26.2 {
        Minecraft.getInstance().gui.setScreen(screen);
        //?} else {
        /*Minecraft.getInstance().setScreen(screen);
        *///?}
    }
}
