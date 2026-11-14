package dev.albedo.modern.mixin;

//? if >=1.21.6 {
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The GUI scale for which the item atlas was drawn; a scale that differs from the window's makes the game draw it again. */
@Mixin(GuiRenderer.class)
public interface GuiRendererAccessor {
    @Accessor("cachedGuiScale")
    void albedo$setCachedGuiScale(int scale);
}
//?}
