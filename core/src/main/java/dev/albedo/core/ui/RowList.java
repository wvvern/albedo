package dev.albedo.core.ui;

/** A scrolling list of rows of one height, which stops drawing after a row that changed the list. */
final class RowList {
    private RowList() {}

    /** Draws one row and returns true when a click changed the list. */
    interface Row {
        boolean draw(int index, int x, int y, int w, int h);
    }

    /** One row of a form at y, which does not change the list. */
    interface Line {
        void draw(int y);
    }

    /**
     * Draws the visible rows of {@code count}, each {@code rowH - 2} high, with a scrollbar when they do not all fit.
     *
     * @param rowH the distance from one row to the next
     */
    static void draw(Ui ui, String id, int x, int y, int w, int h, int count, int rowH, Row row) {
        int visible = Math.max(1, h / rowH);
        int first = ui.scroll(id, x, y, w, h, count, rowH);
        int listW = count > visible ? w - 6 : w;
        for (int i = first; i < Math.min(count, first + visible); i++) {
            if (row.draw(i, x, y + (i - first) * rowH, listW, rowH - 2)) return;
        }
    }
}
