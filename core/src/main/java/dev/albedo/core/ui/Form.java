package dev.albedo.core.ui;

/** A column of rows with a muted label at the left and a control at {@link #lx()}, as wide as {@link #cw()}. */
final class Form {
    /** How much further right the labels of {@link #indented()} rows start, under the title of a section heading drawn at x - 4. */
    static final int INDENT = 8;

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
        return slider(id, y, label, tip, value, value + "%");
    }

    /** A labelled 0 to 100 slider on its own row with {@code valueText} at its right, which returns the value after a click or drag. */
    int slider(String id, int y, String label, String tip, int value, String valueText) {
        label(y, Ui.SLIDER_H, label, tip);
        return ui.slider(id, lx(), y, cw(), value, valueText, tip);
    }

    /** A labelled switch on its own row, where a click on the label flips it too, which returns the new state. */
    boolean toggle(String id, int y, String label, String tip, boolean on) {
        label(y, Ui.H, label, tip);
        boolean flipped = ui.toggle(id, lx(), y + 2, on, tip) != on;
        return (flipped || ui.press(id + ".label", x, y, labelW, Ui.H)) != on;
    }

    /** A labelled slider from {@code min} to {@code max} on its own row, which returns the value after a click or drag. */
    int range(String id, int y, String label, String tip, int value, int min, int max, String unit) {
        label(y, Ui.SLIDER_H, label, tip);
        return ui.rangeSlider(id, lx(), y, cw(), value, min, max, unit, tip);
    }

    /** The height of {@link #sliders} for {@code n} sliders, the gap below them included. */
    static int slidersHeight(int n, boolean oneRow) {
        return (oneRow ? Ui.SLIDER_H : rowsHeight(n) - 3) + Ui.GAP;
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
        int partW = (cw() - (n - 1) * Ui.GAP) / n;
        ui.text(title, x, y + 2, Ui.MUTED);
        for (int i = 0; i < n; i++) {
            int sx = lx() + i * (partW + Ui.GAP), sw = partW;
            // Each part names its slider, in full when the track keeps room and by its initial when not.
            String name = sw - ui.width(labels[i]) - 4 >= ui.width("100%") + 36 ? labels[i] : labels[i].substring(0, 1);
            int nameW = ui.width(name) + 4;
            ui.text(name, sx, y + 2, Ui.MUTED);
            ui.tip(sx, y, nameW, Ui.SLIDER_H, tips[i]);
            values[i] = ui.slider(id + "." + i, sx + nameW, y, sw - nameW, values[i], tips[i]);
        }
        return y + slidersHeight(n, true);
    }
}
