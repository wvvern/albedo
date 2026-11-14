package dev.albedo.core.ui;

import java.util.function.IntFunction;

/** The rows of a list that opens over the page: one ghost button per row, with the selected row lit. */
final class DropdownRows {
    /** The height of one row. */
    static final int ROW = 11;

    private DropdownRows() {}

    /** Draws the text of one row after its button. */
    interface RowText {
        void draw(int index, int x, int y, int w);
    }

    /** Starts the overlay and draws its panel, in which the caller draws the rows and then ends the overlay. */
    static void open(Ui ui, Rect area) {
        ui.beginOverlay();
        ui.panel(area, Ui.BORDER, Ui.PANEL);
    }

    /**
     * Draws the rows from {@code first} that fit in {@code rows}.
     *
     * @param marked the row of the value in effect, which gets an accent mark, or -1
     * @param id the button id of a row
     * @return the clicked row, or -1
     */
    static int draw(Ui ui, Rect rows, int first, int count, int selected, int marked, IntFunction<String> id, RowText rowText) {
        int clicked = -1;
        for (int i = first; i < Math.min(count, first + rows.h / ROW); i++) {
            int ry = rows.y + (i - first) * ROW;
            if (i == selected) ui.fill(rows.x, ry, rows.w, ROW, Ui.CARD_HOVER);
            if (i == marked) ui.fill(rows.x, ry + 1, 2, ROW - 2, Ui.ACCENT);
            if (ui.button(id.apply(i), rows.x, ry, rows.w, ROW, "", Ui.Style.GHOST, true, null)) clicked = i;
            rowText.draw(i, rows.x, ry, rows.w);
        }
        return clicked;
    }
}
