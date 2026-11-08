package dev.singlecolor.neoforge;

import dev.singlecolor.core.SingleColor;
import dev.singlecolor.modern.ModernHooks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** The whole NeoForge-specific surface. */
@Mod(value = SingleColor.MOD_ID, dist = Dist.CLIENT)
public final class SingleColorNeoForge {
    public SingleColorNeoForge() {
        ModernHooks.bootstrap();
    }
}
