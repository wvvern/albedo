package dev.albedo.legacy.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The game timer, which gives the part of a tick from the last tick to the frame, and the resize of the frame for the harness. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("timer")
    Timer albedo$timer();

    @Invoker("resize")
    void albedo$resize(int width, int height);
}
