package dev.albedo.modern;

import dev.albedo.core.ui.Canvas;
import net.minecraft.client.gui.Font;

/** The parts of a settings screen canvas that do not change with the draw target: the font, the size and the images. */
abstract class FontCanvas implements Canvas {
    final Font font;
    final CanvasImages images;
    private final int width;
    private final int height;

    FontCanvas(Font font, int width, int height, CanvasImages images) {
        this.font = font;
        this.width = width;
        this.height = height;
        this.images = images;
    }

    @Override public final int width() { return width; }
    @Override public final int height() { return height; }
    @Override public final int textWidth(String text) { return font.width(text); }
}
