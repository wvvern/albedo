package dev.albedo.modern;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** The cloud option and the cloud renderer, whose getters have other names in other versions. */
final class ModernOptions {
    private ModernOptions() {}

    /** The cloud option as the game reads it, after {@code CloudsMixin}. */
    static CloudStatus cloudStatus(Options options) {
        //? if >=26.1 {
        return options.getCloudStatus();
        //?} else {
        /*return options.getCloudsType();
        *///?}
    }

    /** The cloud renderer of the level renderer, or null before 1.21.2, where the level renderer draws the clouds itself. */
    static Object cloudRenderer() {
        //? if >=26.2 {
        return Minecraft.getInstance().levelRenderer.cloudRenderer();
        //?} elif >=1.21.2 {
        /*return Minecraft.getInstance().levelRenderer.getCloudRenderer();
        *///?} else {
        /*return null;
        *///?}
    }
}
