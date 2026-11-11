package dev.albedo.forge.dev;

import com.mojang.math.Transformation;
import net.minecraftforge.client.extensions.IForgeTransformationMatrix;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Only for the harness run of Forge 1.16.5: Architectury Loom keeps the SRG names of two calls that the Forge patches
 * moved to an interface, so this mixin gives the game the two methods with those names.
 */
@Mixin(Transformation.class)
public abstract class TransformationMixin implements IForgeTransformationMatrix {
    public Transformation func_227987_b_() {
        return inverse();
    }

    public Transformation func_227985_a_(Transformation other) {
        return compose(other);
    }
}
