package dev.albedo.core.ui;

import dev.albedo.core.Colors;

/** A color picker with a full and a compact form that share one hex input, so that a resize keeps the typed text. */
final class ColorPicker {
    private static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFC8C8C8, 0xFF8C8C8C, 0xFF505050, 0xFF141414, 0xFF000000, 0xFF6D4C41, 0xFFFF7043,
            0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF7CB342, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA,
    };
    /** The smallest square of the full picker. */
    static final int MIN_SQUARE = 24;
    private static final int GAP = 6;
    /** The widest saturation and brightness square. */
    private static final int SQUARE_MAX_W = 84;
    /** The width of the large preview beside the hex input. */
    private static final int PREVIEW_W = 22;
    /** The size of one palette swatch. */
    private static final int SWATCH = 9;

    /** The picker's own HSV, kept apart from the color so that hue survives at zero saturation or brightness. */
    private static final class Hsv {
        float hue, sat, val;
        /** The color these values show, or null before the first sync. */
        Integer color;

        void sync(int argb) {
            float[] hsv = Ui.toHsv(argb);
            if (hsv[1] > 0 && hsv[2] > 0) hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
            color = argb;
        }
    }

    private final Ui ui;
    private final Hsv hsv = new Hsv();
    private final ColorField field;

    ColorPicker(Ui ui) {
        this.ui = ui;
        this.field = new ColorField(ui);
    }

    /** The height of the full picker with a square of {@code sqH}, gap included. */
    static int height(int sqH) { return sqH + GAP; }

    /** The height of the compact picker, gap included. */
    static int compactHeight() { return Ui.H + GAP; }

    /** One row with a swatch and the hex input, for a window too short for the full picker, which returns the color. */
    int compact(String id, int x, int y, int w, int argb) {
        return field.draw(id + ".hex", x, y, w, argb);
    }

    /** Draws a saturation and brightness square, hue and opacity bars, a hex input and a palette, and returns the color. */
    int draw(String id, int x, int y, int w, int sqH, int color) {
        if (hsv.color == null || hsv.color != color) hsv.sync(color);
        int alpha = Colors.alpha(color);
        int sqW = Math.min(SQUARE_MAX_W, w / 3);

        // saturation (x) / brightness (y) square, drawn in 3px cells
        for (int yy = 0; yy < sqH; yy += 3) {
            for (int xx = 0; xx < sqW; xx += 3) {
                int cell = Ui.hsv(hsv.hue, xx / (float) (sqW - 1), 1 - yy / (float) (sqH - 1));
                ui.fill(x + xx, y + yy, Math.min(3, sqW - xx), Math.min(3, sqH - yy), cell);
            }
        }
        int kx = x + Math.round(hsv.sat * (sqW - 1)), ky = y + Math.round((1 - hsv.val) * (sqH - 1));
        ui.outline(kx - 2, ky - 2, 5, 5, hsv.val > 0.5f ? 0xFF000000 : 0xFFFFFFFF);
        ui.tip(x, y, sqW, sqH, "Saturation and brightness");
        float[] sv = ui.dragArea(id + ".sv", x, y, sqW, sqH);
        if (sv != null) {
            hsv.sat = sv[0];
            hsv.val = 1 - sv[1];
        }

        int hx = x + sqW + 4;
        for (int yy = 0; yy < sqH; yy += 2) ui.fill(hx, y + yy, 8, Math.min(2, sqH - yy), Ui.hsv(yy / (float) sqH, 1, 1));
        ui.fill(hx - 1, y + Math.round(hsv.hue * (sqH - 1)), 10, 1, 0xFFFFFFFF);
        ui.tip(hx, y, 8, sqH, "Hue");
        float[] hv = ui.dragArea(id + ".hue", hx, y, 8, sqH);
        if (hv != null) hsv.hue = Math.min(hv[1], 0.999f);

        int ax = hx + 12;
        ui.checker(ax, y, 8, sqH);
        int rgb = Ui.hsv(hsv.hue, hsv.sat, hsv.val);
        for (int yy = 0; yy < sqH; yy += 2) ui.fill(ax, y + yy, 8, Math.min(2, sqH - yy), Colors.withAlpha(rgb, 255 - yy * 255 / sqH));
        ui.fill(ax - 1, y + Math.round((1 - alpha / 255f) * (sqH - 1)), 10, 1, 0xFFFFFFFF);
        ui.tip(ax, y, 8, sqH, "Opacity " + Math.round(alpha * 100 / 255f) + "%");
        float[] av = ui.dragArea(id + ".alpha", ax, y, 8, sqH);
        if (av != null) alpha = Math.round((1 - av[1]) * 255);

        // Dragging only the opacity keeps the exact RGB (the HSV round trip could shift it slightly).
        int next = Colors.withAlpha(av != null && sv == null && hv == null ? color : rgb, alpha);
        if (next != color) hsv.color = next;

        // The large preview is left out when it would make the hex field too narrow to show a whole color.
        int px = ax + 14, pw = Math.max(40, x + w - px);
        int hexW = Math.min(ColorField.HEX_W, pw), previewW = pw - hexW >= PREVIEW_W ? PREVIEW_W : 0;
        if (previewW > 0) ui.swatch(px, y, 18, 18, next);
        int typed = field.hex(id + ".hex", px + previewW, y + 2, hexW, color);
        if (typed != color) {
            hsv.sync(typed);
            next = typed;
        }
        int sw = SWATCH, perRow = Math.max(1, Math.min(8, (pw + 2) / (sw + 2)));
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = px + (i % perRow) * (sw + 2), sy = y + 22 + (i / perRow) * (sw + 2);
            if (sy + sw > y + sqH) break; // rows that do not fit beside the square are left out, never drawn over the next row
            boolean current = (PALETTE[i] & 0xFFFFFF) == (color & 0xFFFFFF);
            boolean clicked = ui.button(id + ".pal." + i, sx - 1, sy - 1, sw + 2, sw + 2, "", Ui.Style.GHOST, true, Colors.format(PALETTE[i]));
            if (current) ui.outline(sx - 1, sy - 1, sw + 2, sw + 2, Ui.ACCENT);
            ui.swatch(sx, sy, sw, sw, PALETTE[i]);
            if (clicked) {
                next = Colors.withAlpha(PALETTE[i], alpha);
                hsv.sync(next);
            }
        }
        return next;
    }
}
