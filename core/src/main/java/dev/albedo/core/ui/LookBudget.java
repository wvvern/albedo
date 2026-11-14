package dev.albedo.core.ui;

/** The heights of the look rows, where the color picker gives up height first so that the closed rows below it stay in view. */
final class LookBudget {
    /** The height of the Recolor sliders on their own rows, with the gap under them. */
    static final int RECOLOR_H = Form.slidersHeight(Labels.RECOLOR_LABELS.length, false);
    private static final int MAX_SQUARE = 44;

    /** The height of the square of the full color picker. */
    final int sqH;
    /** Whether the full color picker fits, else the compact one shows. */
    final boolean full;
    final int colorH;
    /** Whether the Recolor sliders go on one row, which a very short window needs; their tooltips then name them. */
    final boolean oneRow;
    /** The height that is left under all rows. */
    final int left;

    /**
     * @param viewH the height of the column
     * @param fixed the height of the rows that keep their height: style, shape, the closed Effects heading and the rows under the look
     * @param recolor whether the Recolor sliders show
     */
    LookBudget(int viewH, int fixed, boolean recolor) {
        int room = viewH - fixed - Ui.GAP - (recolor ? RECOLOR_H : 0);
        sqH = Math.min(MAX_SQUARE, room);
        full = room >= ColorPicker.MIN_SQUARE;
        colorH = full ? ColorPicker.height(sqH) : ColorPicker.compactHeight();
        oneRow = viewH - fixed - colorH < RECOLOR_H;
        left = viewH - fixed - colorH - (recolor ? recolorHeight() : 0);
    }

    /** The height of the Recolor sliders, with the gap under them. */
    int recolorHeight() {
        return Form.slidersHeight(Labels.RECOLOR_LABELS.length, oneRow);
    }
}
