package dev.albedo.core.ui;

import java.util.List;

/** The tooltip of a {@link Ui}: the text a widget asks for in this frame, and since when the pointer rests on it. */
final class Tooltip {
    /** Time in ms a pointer must rest on a widget before its tooltip shows. */
    int delay = 400;
    private String wanted;
    /** Show the text now, with no delay (an armed delete button explains its second click). */
    private boolean now;
    /** The widget that asked for the text, which the tooltip does not cover. */
    private Rect control;
    private String shown;
    private long since;
    /** After a click the result matters more than the explanation: no tooltip until the pointer moves. */
    private boolean hiddenByClick;
    /** The area that tooltips stay in: the screen, or the panel that the owner sets each frame. */
    private Rect bounds;

    /** Clears the request of the last frame, and restarts the delay when the pointer moved. */
    void begin(boolean pointerMoved, Rect screen) {
        if (pointerMoved) {
            since = Ui.now();
            hiddenByClick = false;
        }
        wanted = null;
        control = null;
        now = false;
        bounds = screen;
    }

    /** Keeps the tooltips of this frame inside {@code area}, which must be inside the screen. */
    void bounds(Rect area) { bounds = area; }

    /** Asks for {@code text} beside {@code widget} in this frame, after the delay. */
    void tip(Rect widget, String text) {
        wanted = text;
        control = widget;
    }

    /** Asks for {@code text} beside {@code widget} in this frame, with no delay. */
    void tipNow(Rect widget, String text) {
        tip(widget, text);
        now = true;
    }

    void hideUntilMove() { hiddenByClick = true; }

    /** A tooltip near the pointer, after the pointer rests for {@link #delay} ms; wrapped, kept in the bounds and off its widget. */
    void draw(Ui ui, int px, int py) {
        if (wanted == null) {
            shown = null;
            return;
        }
        if (!wanted.equals(shown)) {
            shown = wanted;
            since = Ui.now();
        }
        if (!now && (hiddenByClick || Ui.now() - since < delay)) return;
        Rect b = bounds;
        Rect ctl = control != null ? control : new Rect(px, py, 1, 1);
        // An armed button is clicked again, and the rows under it often hold more of the same buttons, so its tip goes beside it.
        if (now && beside(ui, b, ctl)) return;
        List<String> lines = ui.wrap(wanted, Math.min(170, b.w - 8));
        int bw = boxWidth(ui, lines), bh = boxHeight(lines);
        int x = Math.max(b.x + 2, Math.min(px + 6, b.x + b.w - bw - 2));
        // A small widget keeps its whole area free; a large one (a list, a picker) only the point under the pointer.
        boolean small = ctl.h <= 2 * Ui.H;
        int below = small ? Math.max(py + 12, ctl.bottom() + 2) : py + 12;
        int above = (small ? Math.min(py, ctl.y) : py) - bh - 4;
        int y = below + bh <= b.bottom() - 2 ? below : Math.max(b.y + 2, above);
        box(ui, lines, x, y, bw, bh);
    }

    /** Draws the tooltip left or right of the control when it fits there, and returns whether it did. */
    private boolean beside(Ui ui, Rect b, Rect ctl) {
        int leftRoom = ctl.x - 3 - (b.x + 2), rightRoom = b.x + b.w - 2 - (ctl.x + ctl.w + 3);
        boolean left = leftRoom >= rightRoom;
        int room = Math.min(170, Math.max(leftRoom, rightRoom));
        if (room < 60) return false;
        List<String> lines = ui.wrap(wanted, room - 8);
        int bw = boxWidth(ui, lines), bh = boxHeight(lines);
        if (bh > b.h - 4) return false;
        int x = left ? ctl.x - 3 - bw : ctl.x + ctl.w + 3;
        int y = Math.max(b.y + 2, Math.min(ctl.y + (ctl.h - bh) / 2, b.bottom() - bh - 2));
        box(ui, lines, x, y, bw, bh);
        return true;
    }

    private static int boxWidth(Ui ui, List<String> lines) {
        int w = 0;
        for (String l : lines) w = Math.max(w, ui.width(l));
        return w + 8;
    }

    private static int boxHeight(List<String> lines) {
        return lines.size() * (Ui.LINE + 1) + 5;
    }

    private static void box(Ui ui, List<String> lines, int x, int y, int bw, int bh) {
        ui.round(x, y, bw, bh, Ui.BORDER);
        ui.round(x + 1, y + 1, bw - 2, bh - 2, Ui.TOOLTIP);
        for (int i = 0; i < lines.size(); i++) ui.text(lines.get(i), x + 4, y + 3 + i * (Ui.LINE + 1), Ui.TEXT);
    }
}
