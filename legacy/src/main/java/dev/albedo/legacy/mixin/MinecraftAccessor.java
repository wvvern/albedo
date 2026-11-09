package dev.albedo.legacy.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The game timer, which gives the part of a tick from the last tick to the frame. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("timer")
    Timer albedo$timer();
}
