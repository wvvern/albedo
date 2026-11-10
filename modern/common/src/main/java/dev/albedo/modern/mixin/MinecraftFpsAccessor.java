package dev.albedo.modern.mixin;

//? if <1.19.3 {
/*import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The frame rate of the last second, which Minecraft.getFps gives from 1.19.3.
@Mixin(Minecraft.class)
public interface MinecraftFpsAccessor {
    @Accessor("fps")
    static int albedo$fps() {
        throw new AssertionError();
    }
}
*///?}
