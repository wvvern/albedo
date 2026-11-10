package dev.albedo.core.ui;

import dev.albedo.core.Colors;

/** A hex input for one color, which keeps the typed text while it has the focus; each color on a page has its own. */
final class ColorField {
    private final Ui ui;
    private final TextField hex = new TextField(9, c -> Character.digit(c, 16) >= 0 || c == '#');

    ColorField(Ui ui) {
        this.ui = ui;
    }

    /** The hex input alone, which returns the typed color, or {@code argb} while the text is not a whole color. */
    int hex(String id, int x, int y, int w, int argb) {
        if (!ui.isFocused(id)) hex.set(Colors.format(argb));
        if (ui.textField(id, hex, x, y, w, Ui.H, "#RRGGBB", "Type a color: #RGB, #RRGGBB or #AARRGGBB")) {
            try {
                return Colors.parse(hex.text);
            } catch (IllegalArgumentException ignored) {
                // keep typing
            }
        }
        return argb;
    }

    /** A swatch and the hex input on one row, which returns the color. */
    int draw(String id, int x, int y, int w, int argb) {
        ui.swatch(x, y + 2, 10, 10, argb);
        return hex(id, x + 14, y, Math.min(62, w - 14), argb);
    }
}
