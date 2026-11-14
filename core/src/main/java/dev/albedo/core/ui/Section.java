package dev.albedo.core.ui;

/** A section heading that opens and closes the rows under it; only a click on the heading changes it. */
final class Section {
    private final Ui ui;
    private final String id;
    private final Runnable relayout;
    private boolean open;

    /** @param relayout tells the screen that the rows under the heading moved */
    Section(Ui ui, String id, Runnable relayout) {
        this.ui = ui;
        this.id = id;
        this.relayout = relayout;
    }

    boolean isOpen() { return open; }

    /** Draws the heading at x - 4, under which the {@link Form#indented()} rows line up, and returns true when a click flipped it. */
    boolean header(int x, int y, int w, String title, String summary, String tip) {
        if (ui.sectionHeader(id, x - 4, y, w + 4, title, summary, open, tip) == open) return false;
        open = !open;
        relayout.run();
        return true;
    }
}
