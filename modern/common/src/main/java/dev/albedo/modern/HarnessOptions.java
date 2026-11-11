package dev.albedo.modern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;

/** Development-only: the options that the test harness sets, which became option objects in 1.19 and 1.19.3. */
final class HarnessOptions {
    private HarnessOptions() {}

    static void quiet() {
        Options o = Minecraft.getInstance().options;
        //? if >=1.19.3 {
        if (o.getSoundSourceOptionInstance(SoundSource.MASTER).get() != 0.0) o.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        //?} else {
        /*if (o.getSoundSourceVolume(SoundSource.MASTER) != 0f) o.setSoundCategoryVolume(SoundSource.MASTER, 0f);
        *///?}
        //? if >=1.19 {
        if (o.narrator().get() != NarratorStatus.OFF) o.narrator().set(NarratorStatus.OFF);
        //?} else {
        /*o.narratorStatus = NarratorStatus.OFF;
        *///?}
        // A window on a hidden desktop never has focus; pausing would stop the command queue.
        o.pauseOnLostFocus = false;
    }

    static void setFov(int degrees) {
        //? if >=1.19 {
        Minecraft.getInstance().options.fov().set(degrees);
        //?} else {
        /*Minecraft.getInstance().options.fov = degrees;
        *///?}
    }

    static void uncapFrameRate() {
        Options o = Minecraft.getInstance().options;
        // The option value 260 is "Unlimited".
        //? if >=1.19 {
        o.framerateLimit().set(260);
        o.enableVsync().set(false);
        //?} else {
        /*o.framerateLimit = 260;
        o.enableVsync = false;
        *///?}
        //? if >=1.21.2 {
        o.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
        //?}
    }
}
