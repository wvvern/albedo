package dev.albedo.core.ui;

/** A column of rows with a muted label at the left and a control at {@link #lx()}, as wide as {@link #cw()}. */
final class Form {
    /** How much further right the labels of {@link #indented()} rows start, under the title of a section heading drawn at x - 4. */
    static final int INDENT = 8;
    private static final int GAP = 6;

    /** A labelled percent slider on its own row, with an id that does not change when other rows come or go. */
    static final class Slider {
        final String id;
        final String label;
        final String tip;

        Slider(String id, String label, String tip) {
            this.id = id;
            this.label = label;
            this.tip = tip;
        }
    }

    private final Ui ui;
    private final int x, w, labelW;

    /** @param labelW the width of the label column, gap included */
    Form(Ui ui, int x, int w, int labelW) {
        this.ui = ui;
        this.x = x;
        this.w = w;
        this.labelW = labelW;
    }

    /** The left edge of the controls. */
    int lx() { return x + labelW; }

    /** The width of the controls. */
    int cw() { return w - labelW; }

    /** The same rows with labels {@link #INDENT} further right and the controls at the same {@link #lx()}. */
    Form indented() { return new Form(ui, x + INDENT, w - INDENT, labelW - INDENT); }

    /** Draws the vertical line that shows which rows are under a heading, for the rows of {@link #indented()}. */
    void guide(int y, int h) {
        ui.fill(x + 2, y, 1, h, Ui.BORDER);
    }

    /** Draws a label for a row of height {@code h}, with {@code tip} as the tooltip of the label. */
    void label(int y, int h, String text, String tip) {
        ui.text(text, x, y + (h - Ui.LINE) / 2 + 1, Ui.MUTED);
        ui.tip(x, y, labelW, h, tip);
    }

    /** A labelled percent slider on its own row, which returns the value after a click or drag. */
    int slider(String id, int y, String label, String tip, int value) {
        label(y, Ui.SLIDER_H, label, tip);
        return ui.slider(id, lx(), y, cw(), value, tip);
    }

    /** The height of {@link #sliders} or {@link #rows} for {@code n} slider rows, the gap below them included. */
    static int slidersHeight(int n, boolean oneRow) {
        return (oneRow ? Ui.SLIDER_H : rowsHeight(n) - 3) + GAP;
    }

    /** The height of {@code n} sliders on their own rows, with the small gap after each but not the gap below a group. */
    static int rowsHeight(int n) {
        return n * (Ui.SLIDER_H + 3);
    }

    /**
     * Percent sliders on their own rows, or on one row under {@code title} that names them in their tooltips, which
     * change {@code values} in place.
     *
     * @return the next y
     */
    int sliders(String id, String title, String[] labels, String[] tips, int[] values, int y, boolean oneRow) {
        int n = labels.length;
        if (!oneRow) {
            for (int i = 0; i < n; i++) values[i] = slider(id + "." + i, y + rowsHeight(i), labels[i], tips[i], values[i]);
            return y + slidersHeight(n, false);
        }
        int partW = (cw() - (n - 1) * GAP) / n;
        ui.text(title, x, y + 2, Ui.MUTED);
        for (int i = 0; i < n; i++) {
            int sx = lx() + i * (partW + GAP), sw = partW;
            // Each part names its slider, in full when the track keeps room and by its initial when not.
            String name = sw - ui.width(labels[i]) - 4 >= ui.width("100%") + 36 ? labels[i] : labels[i].substring(0, 1);
            int nameW = ui.width(name) + 4;
            ui.text(name, sx, y + 2, Ui.MUTED);
            ui.tip(sx, y, nameW, Ui.SLIDER_H, tips[i]);
            values[i] = ui.slider(id + "." + i, sx + nameW, y, sw - nameW, values[i], tips[i]);
        }
        return y + slidersHeight(n, true);
    }

    /**
     * Sliders on their own rows, which change {@code values} in place.
     *
     * @return the y below the last row
     */
    int rows(Slider[] sliders, int[] values, int y) {
        for (int i = 0; i < sliders.length; i++) {
            Slider s = sliders[i];
            values[i] = slider(s.id, y + rowsHeight(i), s.label, s.tip, values[i]);
        }
        return y + rowsHeight(sliders.length);
    }
}
