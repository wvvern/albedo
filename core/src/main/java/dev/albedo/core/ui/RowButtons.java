package dev.albedo.core.ui;

/** The up, down and delete buttons at the right end of a list row. */
final class RowButtons {
    /** The width that the buttons take from the right end of the row. */
    static final int W = 50;

    /** The button that a click hit, if any. */
    enum RowAction { NONE, UP, DOWN, DELETE }

    private RowButtons() {}

    /**
     * Draws the buttons from {@code bx} and returns the clicked one, where the buttons after a clicked one are not drawn.
     *
     * @param id the start of the widget ids, such as "sky", and {@code key} names the row
     * @param confirmTip the tip of the armed delete button, which deletes on a second click
     */
    static RowAction draw(Ui ui, String id, String key, int bx, int y, boolean canUp, boolean canDown, String upTip, String downTip,
            String deleteTip, String confirmTip) {
        if (ui.button(id + ".up." + key, bx, y + 1, 15, 12, "^", Ui.Style.GHOST, canUp, upTip)) return RowAction.UP;
        if (ui.button(id + ".down." + key, bx + 16, y + 1, 15, 12, "v", Ui.Style.GHOST, canDown, downTip)) return RowAction.DOWN;
        if (ui.confirmButton(id + ".del." + key, bx + 34, y + 1, 15, 12, "x", deleteTip, confirmTip)) return RowAction.DELETE;
        return RowAction.NONE;
    }
}
