package dev.albedo.core.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** An immediate-mode GUI where each widget call draws the widget and returns what the user did to it since the last frame. */
final class Ui {
    static final int SCRIM = 0x99000000;
    static final int BG = 0xF2121519;
    static final int PANEL = 0xFF181C22;
    static final int CARD = 0xFF20252D;
    static final int CARD_HOVER = 0xFF2A303A;
    static final int BORDER = 0xFF323944;
    static final int TEXT = 0xFFE9ECF1;
    static final int MUTED = 0xFF9AA3B3;
    static final int FAINT = 0xFF6A7282;
    static final int ACCENT = 0xFF4D8EFF;
    static final int ACCENT_HOVER = 0xFF6FA3FF;
    static final int DANGER = 0xFFE5636B;
    static final int WARN = 0xFFE8B04B;
    static final int TOOLTIP = 0xFA0B0D10;
    static final int LINE = 9;
    /** Height of buttons, fields and segmented controls. */
    static final int H = 14;
    /** Space between rows of a wrapped segmented control. */
    private static final int ROW_GAP = 2;
    /** Least space around a label inside a segment. */
    private static final int SEG_PAD = 6;
    /** Time in ms a delete button stays armed after the first click. */
    private static final int CONFIRM_MS = 3000;

    /** Time in ms a pointer must rest on a widget before its tooltip shows. 0 in tests. */
    int tipDelay = 400;

    enum Style { NORMAL, PRIMARY, DANGER, GHOST }

    interface Clipboard {
        String get();
    }

    private static final class KeyPress {
        final UiKey key;
        final boolean ctrl;

        KeyPress(UiKey key, boolean ctrl) {
            this.key = key;
            this.ctrl = ctrl;
        }
    }

    /** The left button, whose press goes to the first widget under the pointer, which then owns the drag until release. */
    private static final class Pointer {
        int x = -1, y = -1;
        boolean held;
        /** A press that no widget has claimed yet. */
        boolean click;
        /** A press arrived before this frame, claimed or not. */
        boolean clickedThisFrame;
        /** The id of the widget that claimed the press, or null. */
        String owner;
    }

    /** A delete button after its first click, which stays armed for {@link #CONFIRM_MS} ms. */
    private static final class Armed {
        final String id;
        final long at;

        Armed(String id, long at) {
            this.id = id;
            this.at = at;
        }
    }

    /** The tooltip: the text a widget asks for in this frame and since when the pointer rests on it. */
    private static final class Tip {
        String wanted;
        /** Show the text now, with no delay (an armed delete button explains its second click). */
        boolean now;
        /** The widget that asked for the text, which the tooltip does not cover. */
        Rect control;
        String shown;
        long since;
        /** After a click the result matters more than the explanation: no tooltip until the pointer moves. */
        boolean hiddenByClick;
    }

    Canvas c;
    private final Pointer pointer = new Pointer();
    private final Tip tip = new Tip();
    private Armed armed;
    private String focus;
    /** The focus at the start of the frame, which {@link #end} drops when no text field drew it. */
    private String focusAtBegin;
    private boolean focusDrawn;
    private int wheel;
    /** A list drawn over other widgets (a dropdown); widgets drawn before it ignore the pointer inside it. */
    private Rect overlay;
    private boolean drawingOverlay;
    /** The last overlay area of this frame, kept after the overlay ends, so that later drawing can stay off it. */
    private Rect overlayDrawn;
    /** The area that tooltips stay in: the screen, or the panel that the owner sets each frame. */
    private Rect tipBounds;
    private final List<KeyPress> keys = new ArrayList<KeyPress>();
    private final StringBuilder typed = new StringBuilder();
    private final Map<String, Integer> scrolls = new HashMap<String, Integer>();
    private final Clipboard clipboard;

    Ui(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    // ---------------------------------------------------------------- events (between frames; the pointer position
    // comes from each frame's render call)

    void mousePressed() {
        pointer.held = true;
        pointer.click = true;
    }

    void mouseReleased() {
        pointer.held = false;
        pointer.owner = null;
    }

    void scrolled(double amount) {
        wheel += amount > 0 ? 1 : amount < 0 ? -1 : 0;
    }

    void key(UiKey key, boolean ctrl) {
        keys.add(new KeyPress(key, ctrl));
    }

    void typed(char ch) {
        if (ch >= ' ' && ch != 127) typed.append(ch);
    }

    // ---------------------------------------------------------------- frame

    void begin(Canvas canvas, int mouseX, int mouseY) {
        this.c = canvas;
        pointer.clickedThisFrame = pointer.click;
        if (mouseX != pointer.x || mouseY != pointer.y) {
            tip.since = now();
            tip.hiddenByClick = false;
        }
        pointer.x = mouseX;
        pointer.y = mouseY;
        tip.wanted = null;
        tip.control = null;
        tip.now = false;
        overlayDrawn = null;
        tipBounds = new Rect(0, 0, canvas.width(), canvas.height());
        if (armed != null && now() - armed.at > CONFIRM_MS) armed = null;
        focusAtBegin = focus;
        focusDrawn = false;
    }

    /** Ends the frame: an unclaimed press clears focus and armed buttons, unclaimed input is dropped, and the tooltip draws. */
    void end() {
        overlay = null;
        drawingOverlay = false;
        if (pointer.click) {
            focus = null;
            armed = null;
            pointer.click = false;
        }
        if (focus != null && focus.equals(focusAtBegin) && !focusDrawn) focus = null;
        wheel = 0;
        typed.setLength(0);
        keys.clear();
        tooltip();
    }

    /** True when the left button went down since the last frame (before any widget takes the press). */
    boolean clicked() { return pointer.clickedThisFrame; }

    boolean isFocused(String id) { return id.equals(focus); }

    void focus(String id) { focus = id; }

    void clearFocus() { focus = null; }

    /** Clears the focus only when {@code id} has it, so that a field another widget just focused keeps it. */
    void releaseFocus(String id) {
        if (id.equals(focus)) focus = null;
    }

    /** Takes an unclaimed key press (e.g. Escape for the screen itself). */
    boolean takeKey(UiKey key) {
        for (Iterator<KeyPress> it = keys.iterator(); it.hasNext(); ) {
            if (it.next().key == key) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /** Disarms an armed delete button, and returns whether one was armed. */
    boolean disarm() {
        if (armed == null) return false;
        armed = null;
        return true;
    }

    boolean hover(int x, int y, int w, int h) {
        if (overlay != null && !drawingOverlay && overlay.contains(pointer.x, pointer.y)) return false;
        return pointerIn(x, y, w, h);
    }

    /** Whether the pointer is in the rectangle, whatever is drawn over it. */
    boolean pointerIn(int x, int y, int w, int h) {
        return new Rect(x, y, w, h).contains(pointer.x, pointer.y);
    }

    // ---------------------------------------------------------------- overlay (dropdowns drawn over other widgets)

    /** Widgets drawn from now until {@link #beginOverlay()} ignore the pointer inside {@code area}. */
    void reserveOverlay(Rect area) {
        overlay = area;
        overlayDrawn = area;
    }

    /** The overlay area that this frame reserved, or null. */
    Rect overlayDrawn() { return overlayDrawn; }

    /** Keeps the tooltips of this frame inside {@code area}, which must be inside the screen. */
    void tipBounds(Rect area) { tipBounds = area; }

    /** Widgets drawn from now on are the overlay itself. */
    void beginOverlay() {
        drawingOverlay = true;
    }

    void endOverlay() {
        overlay = null;
        drawingOverlay = false;
    }

    private boolean press(String id, int x, int y, int w, int h) {
        if (!pointer.click || !hover(x, y, w, h)) return false;
        pointer.click = false;
        pointer.owner = id;
        tip.hiddenByClick = true;
        if (!id.equals(focus)) focus = null;
        if (armed != null && !id.equals(armed.id)) armed = null;
        return true;
    }

    /** Whether the widget {@code id} owns the press and the button is still down. */
    private boolean dragging(String id) {
        return pointer.held && id.equals(pointer.owner);
    }

    /** Sets the tooltip for this frame when the pointer is on the rectangle. */
    void tip(int x, int y, int w, int h, String text) {
        if (text != null && hover(x, y, w, h)) {
            tip.wanted = text;
            tip.control = new Rect(x, y, w, h);
        }
    }

    /** A tooltip near the pointer, after the pointer rests for {@link #tipDelay} ms; wrapped, kept in the tip bounds and off its widget. */
    private void tooltip() {
        if (tip.wanted == null) {
            tip.shown = null;
            return;
        }
        if (!tip.wanted.equals(tip.shown)) {
            tip.shown = tip.wanted;
            tip.since = now();
        }
        if (!tip.now && (tip.hiddenByClick || now() - tip.since < tipDelay)) return;
        Rect b = tipBounds;
        Rect ctl = tip.control != null ? tip.control : new Rect(pointer.x, pointer.y, 1, 1);
        // An armed button is clicked again, and the rows under it often hold more of the same buttons, so its tip goes beside it.
        if (tip.now && tipBeside(b, ctl)) return;
        List<String> lines = wrap(tip.wanted, Math.min(170, b.w - 8));
        int bw = linesWidth(lines) + 8, bh = lines.size() * (LINE + 1) + 5;
        int x = Math.max(b.x + 2, Math.min(pointer.x + 6, b.x + b.w - bw - 2));
        // A small widget keeps its whole area free; a large one (a list, a picker) only the point under the pointer.
        boolean small = ctl.h <= 2 * H;
        int below = small ? Math.max(pointer.y + 12, ctl.bottom() + 2) : pointer.y + 12;
        int above = (small ? Math.min(pointer.y, ctl.y) : pointer.y) - bh - 4;
        int y = below + bh <= b.bottom() - 2 ? below : Math.max(b.y + 2, above);
        tipBox(lines, x, y, bw, bh);
    }

    /** Draws the tooltip left or right of the control when it fits there, and returns whether it did. */
    private boolean tipBeside(Rect b, Rect ctl) {
        int leftRoom = ctl.x - 3 - (b.x + 2), rightRoom = b.x + b.w - 2 - (ctl.x + ctl.w + 3);
        boolean left = leftRoom >= rightRoom;
        int room = Math.min(170, Math.max(leftRoom, rightRoom));
        if (room < 60) return false;
        List<String> lines = wrap(tip.wanted, room - 8);
        int bw = linesWidth(lines) + 8, bh = lines.size() * (LINE + 1) + 5;
        if (bh > b.h - 4) return false;
        int x = left ? ctl.x - 3 - bw : ctl.x + ctl.w + 3;
        int y = Math.max(b.y + 2, Math.min(ctl.y + (ctl.h - bh) / 2, b.bottom() - bh - 2));
        tipBox(lines, x, y, bw, bh);
        return true;
    }

    private int linesWidth(List<String> lines) {
        int w = 0;
        for (String l : lines) w = Math.max(w, width(l));
        return w;
    }

    private void tipBox(List<String> lines, int x, int y, int bw, int bh) {
        round(x, y, bw, bh, BORDER);
        round(x + 1, y + 1, bw - 2, bh - 2, TOOLTIP);
        for (int i = 0; i < lines.size(); i++) text(lines.get(i), x + 4, y + 3 + i * (LINE + 1), TEXT);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ---------------------------------------------------------------- drawing helpers

    void fill(int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) c.fill(x, y, x + w, y + h, color);
    }

    /** A rectangle with its corner pixels cut, which reads as softly rounded at GUI scale. */
    void round(int x, int y, int w, int h, int color) {
        fill(x + 1, y, w - 2, h, color);
        fill(x, y + 1, 1, h - 2, color);
        fill(x + w - 1, y + 1, 1, h - 2, color);
    }

    void panel(Rect r, int border, int body) {
        round(r.x, r.y, r.w, r.h, border);
        round(r.x + 1, r.y + 1, r.w - 2, r.h - 2, body);
    }

    void outline(int x, int y, int w, int h, int color) {
        fill(x + 1, y, w - 2, 1, color);
        fill(x + 1, y + h - 1, w - 2, 1, color);
        fill(x, y + 1, 1, h - 2, color);
        fill(x + w - 1, y + 1, 1, h - 2, color);
    }

    void text(String s, int x, int y, int color) {
        c.text(s, x, y, color);
    }

    void textCentered(String s, int cx, int y, int color) {
        c.text(s, cx - c.textWidth(s) / 2, y, color);
    }

    /** Draws the text only when all of it fits in {@code w}; secondary text is left out, not cut. */
    boolean textFit(String s, int x, int y, int w, int color) {
        if (w <= 0 || width(s) > w) return false;
        text(s, x, y, color);
        return true;
    }

    /** Draws the first of {@code options} that fits in {@code w}, if any. */
    void textFirstFit(int x, int y, int w, int color, String... options) {
        for (String s : options) if (textFit(s, x, y, w, color)) return;
    }

    int width(String s) { return c.textWidth(s); }

    int buttonWidth(String label) { return width(label) + 12; }

    int buttonWidth(String... labels) {
        int w = 0;
        for (String l : labels) w = Math.max(w, buttonWidth(l));
        return w;
    }

    /** Splits text into lines at word boundaries; a word longer than {@code w} is shortened. */
    List<String> wrap(String s, int w) {
        List<String> lines = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (width(next) > w && line.length() > 0) {
                lines.add(ellipsize(line.toString(), w));
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        if (line.length() > 0) lines.add(ellipsize(line.toString(), w));
        return lines;
    }

    /** The height {@link #paragraph} would use. */
    int paragraphHeight(String s, int w) {
        return wrap(s, w).size() * (LINE + 1) - 1;
    }

    /** Draws text wrapped at word boundaries within {@code w}; returns the height used. */
    int paragraph(String s, int x, int y, int w, int color) {
        List<String> lines = wrap(s, w);
        for (int i = 0; i < lines.size(); i++) text(lines.get(i), x, y + i * (LINE + 1), color);
        return lines.size() * (LINE + 1) - 1;
    }

    String ellipsize(String s, int max) {
        if (c.textWidth(s) <= max) return s;
        // Binary search for the longest prefix that fits with "..." after it.
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (c.textWidth(s.substring(0, mid) + "...") <= max) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo) + "...";
    }

    /** Light/dark squares behind translucent colors. */
    void checker(int x, int y, int w, int h) {
        fill(x, y, w, h, 0xFFBFBFBF);
        for (int yy = 0; yy < h; yy += 3)
            for (int xx = (yy / 3) % 2 * 3; xx < w; xx += 6)
                fill(x + xx, y + yy, Math.min(3, w - xx), Math.min(3, h - yy), 0xFF7F7F7F);
    }

    /** A color chip; {@code color == null} draws a "hidden" chip. */
    void swatch(int x, int y, int w, int h, Integer color) {
        fill(x, y, w, h, BORDER);
        if (color == null) {
            fill(x + 1, y + 1, w - 2, h - 2, PANEL);
            for (int i = 1; i < Math.min(w, h) - 1; i++) fill(x + i, y + i, 1, 1, DANGER);
            return;
        }
        if ((color >>> 24) < 255) checker(x + 1, y + 1, w - 2, h - 2);
        fill(x + 1, y + 1, w - 2, h - 2, color);
    }

    // ---------------------------------------------------------------- widgets

    boolean button(String id, int x, int y, int w, int h, String label, Style style, boolean enabled, String tipText) {
        boolean hovered = enabled && hover(x, y, w, h);
        int bg;
        switch (style) {
            case PRIMARY: bg = !enabled ? CARD : hovered ? ACCENT_HOVER : ACCENT; break;
            case GHOST: bg = hovered ? CARD_HOVER : 0; break;
            default: bg = hovered ? CARD_HOVER : CARD;
        }
        if (bg != 0) round(x, y, w, h, bg);
        int fg = !enabled ? FAINT : style == Style.DANGER ? DANGER : style == Style.PRIMARY ? 0xFFFFFFFF : TEXT;
        String shown = ellipsize(label, w - 4);
        textCentered(shown, x + w / 2, y + (h - LINE) / 2 + 1, fg);
        tip(x, y, w, h, !shown.equals(label) && tipText == null ? label : tipText);
        return enabled && press(id, x, y, w, h);
    }

    /** A delete button that returns true on a second click within a few seconds of the first, which turns it red. */
    boolean confirmButton(String id, int x, int y, int w, int h, String label, String tipText, String confirmTip) {
        if (armed == null || !armed.id.equals(id)) {
            if (button(id, x, y, w, h, label, Style.DANGER, true, tipText)) armed = new Armed(id, now());
            return false;
        }
        boolean hovered = hover(x, y, w, h);
        round(x, y, w, h, hovered ? 0xFFF07C83 : DANGER);
        textCentered(ellipsize(label, w - 4), x + w / 2, y + (h - LINE) / 2 + 1, 0xFFFFFFFF);
        if (hovered) {
            tip.wanted = confirmTip;
            tip.control = new Rect(x, y, w, h);
            tip.now = true;
        }
        if (!press(id, x, y, w, h)) return false;
        armed = null;
        return true;
    }

    int chipWidth(String label, boolean hasSwatch) {
        return 4 + (hasSwatch ? 10 : 0) + width(label) + 12;
    }

    /**
     * A tag with a swatch, a label and an x, which returns true when its x was clicked.
     *
     * @param dim draws the tag faded, for a pattern that has no effect
     */
    boolean chip(String id, int x, int y, Integer swatch, String label, boolean dim, String tipText) {
        int w = chipWidth(label, swatch != null);
        round(x, y, w, 12, dim ? PANEL : CARD);
        if (dim) outline(x, y, w, 12, BORDER);
        int tx = x + 4;
        if (swatch != null) {
            swatch(tx, y + 2, 8, 8, swatch);
            tx += 10;
        }
        text(label, tx, y + 2, dim ? FAINT : TEXT);
        tip(x, y, w - 10, 12, tipText);
        return button(id + ".x", x + w - 11, y + 1, 10, 10, "x", Style.GHOST, true, "Remove " + label);
    }

    /** A pill switch; returns the new state. */
    boolean toggle(String id, int x, int y, boolean on, String tipText) {
        int w = 18, h = 10;
        boolean hovered = hover(x, y, w, h);
        round(x, y, w, h, on ? (hovered ? ACCENT_HOVER : ACCENT) : (hovered ? FAINT : BORDER));
        round(on ? x + w - 9 : x + 1, y + 1, 8, 8, TEXT);
        tip(x, y, w, h, tipText);
        return press(id, x, y, w, h) != on;
    }

    /** A label with a switch at the right edge; the whole row is clickable. */
    boolean toggleRow(String id, int x, int y, int w, String label, boolean on, String tipText) {
        int h = 12;
        if (hover(x, y, w, h)) round(x, y, w, h, CARD_HOVER);
        text(ellipsize(label, w - 30), x + 4, y + 2, TEXT);
        boolean flipped = toggle(id + ".switch", x + w - 22, y + 1, on, tipText) != on;
        tip(x, y, w, h, tipText);
        return (flipped || press(id, x, y, w, h)) != on;
    }

    /**
     * The heading of a section that opens and closes, which returns the new open state; the whole row is clickable.
     *
     * @param summary muted text at the right edge, such as the values in the closed section, drawn only when it fits
     */
    boolean sectionHeader(String id, int x, int y, int w, String title, String summary, boolean open, String tipText) {
        if (hover(x, y, w, H)) round(x, y, w, H, CARD_HOVER);
        int ty = y + (H - LINE) / 2 + 1, tx = x + 12;
        text(open ? "v" : ">", x + 4, ty, MUTED);
        String shown = ellipsize(title, x + w - 4 - tx);
        text(shown, tx, ty, TEXT);
        if (summary != null) {
            int sw = width(summary), room = x + w - 4 - (tx + width(shown) + 8);
            textFit(summary, x + w - 4 - sw, ty, room, MUTED);
        }
        tip(x, y, w, H, tipText);
        return press(id, x, y, w, H) != open;
    }

    /** Draws a page title with a muted subtitle beside it when it fits, and returns the height it takes. */
    int title(int x, int y, int w, String title, String subtitle) {
        text(title, x, y, TEXT);
        int nameW = width(title) + 8;
        textFit(subtitle, x + nameW, y, w - nameW, MUTED);
        return 13;
    }

    /** The "<" button that goes back to the page above, which returns true when clicked. */
    boolean backButton(String id, int x, int y, String tipText) {
        return button(id, x, y, 13, H, "<", Style.GHOST, true, tipText);
    }

    /** A {@link #segmented} control with one segment for each of {@code values}, which returns the selected value. */
    <E extends Enum<E>> E choice(String id, int x, int y, int w, E[] values, E selected, Function<E, String> label, Function<E, String> tip) {
        int index = segmented(id, x, y, w, words(values, label), Arrays.asList(values).indexOf(selected), words(values, tip));
        return values[index];
    }

    /** The height {@link #choice} uses. */
    <E extends Enum<E>> int choiceHeight(E[] values, Function<E, String> label, int w) {
        return segmentedHeight(words(values, label), w);
    }

    private static <E> String[] words(E[] values, Function<E, String> f) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = f.apply(values[i]);
        return out;
    }

    /** Page tabs, which look different from {@link #segmented} because tabs change the page and segments change a value. */
    int tabs(String id, int x, int y, int w, String[] labels, int selected, String[] tips) {
        int n = labels.length, total = 0;
        for (String l : labels) total += width(l);
        int gap = Math.max(8, Math.min(14, (w - total) / Math.max(1, n)));
        boolean fits = total + gap * n <= w;
        fill(x, y + H - 1, w, 1, BORDER);
        int result = selected, tx = x;
        for (int i = 0; i < n; i++) {
            int tw = fits ? width(labels[i]) + gap : (x + w * (i + 1) / n) - tx;
            boolean hovered = hover(tx, y, tw, H);
            String shown = ellipsize(labels[i], tw - 4);
            text(shown, tx + (fits ? 0 : (tw - width(shown)) / 2), y + 2, i == selected || hovered ? TEXT : MUTED);
            int lw = fits ? width(labels[i]) : tw - 4;
            int lx = fits ? tx : tx + 2;
            if (i == selected) fill(lx, y + H - 2, lw, 2, ACCENT);
            else if (hovered) fill(lx, y + H - 1, lw, 1, FAINT);
            tip(tx, y, tw, H, tips[i]);
            if (press(id + "." + i, tx, y, tw, H)) result = i;
            tx += tw;
        }
        return result;
    }

    /** The height {@link #segmented} uses for these labels: it wraps into rows when they do not fit in one. */
    int segmentedHeight(String[] labels, int w) {
        int rows = segmentRows(labels, w);
        return rows * H + (rows - 1) * ROW_GAP;
    }

    /** Segments sized to their labels, in rows of equal count when one row is too narrow, which return the selected index. */
    int segmented(String id, int x, int y, int w, String[] labels, int selected, String[] tips) {
        int n = labels.length, rows = segmentRows(labels, w);
        int per = (n + rows - 1) / rows;
        int result = selected;
        for (int r = 0; r < rows; r++) {
            int from = r * per, to = Math.min(n, from + per), ry = y + r * (H + ROW_GAP);
            int spare = Math.max(0, w - need(labels, from, to)), sx = x;
            round(x, ry, w, H, CARD);
            for (int i = from; i < to; i++) {
                int k = i - from, count = to - from;
                int sw = i == to - 1 ? x + w - sx : width(labels[i]) + SEG_PAD + spare * (k + 1) / count - spare * k / count;
                boolean hovered = hover(sx, ry, sw, H);
                if (i == selected) round(sx + 1, ry + 1, sw - 2, H - 2, ACCENT);
                else if (hovered) round(sx + 1, ry + 1, sw - 2, H - 2, CARD_HOVER);
                String shown = ellipsize(labels[i], sw - 2);
                textCentered(shown, sx + sw / 2, ry + (H - LINE) / 2 + 1, i == selected ? 0xFFFFFFFF : hovered ? TEXT : MUTED);
                tip(sx, ry, sw, H, tips[i]);
                if (press(id + "." + i, sx, ry, sw, H)) result = i;
                sx += sw;
            }
        }
        return result;
    }

    private int segmentRows(String[] labels, int w) {
        int rows = 1;
        while (rows < labels.length && !rowsFit(labels, rows, w)) rows++;
        return rows;
    }

    private boolean rowsFit(String[] labels, int rows, int w) {
        int per = (labels.length + rows - 1) / rows;
        for (int from = 0; from < labels.length; from += per) {
            if (need(labels, from, Math.min(labels.length, from + per)) > w) return false;
        }
        return true;
    }

    private int need(String[] labels, int from, int to) {
        int need = 0;
        for (int i = from; i < to; i++) need += width(labels[i]) + SEG_PAD;
        return need;
    }

    /**
     * A draggable area; returns the pointer position inside it as fractions {x, y} in [0, 1] while it is pressed or
     * dragged, otherwise null.
     */
    float[] dragArea(String id, int x, int y, int w, int h) {
        if (!press(id, x, y, w, h) && !dragging(id)) return null;
        float fx = Math.max(0f, Math.min(1f, (pointer.x - x) / (float) Math.max(1, w - 1)));
        float fy = Math.max(0f, Math.min(1f, (pointer.y - y) / (float) Math.max(1, h - 1)));
        return new float[]{fx, fy};
    }

    /** The height of a {@link #slider}. */
    static final int SLIDER_H = 12;

    /** A 0 to 100 slider with its value at the right, which returns the value after a click or drag. */
    int slider(String id, int x, int y, int w, int value, String tipText) {
        return slider(id, x, y, w, value, value + "%", tipText);
    }

    /** A slider from 0 to 100 with {@code valueText} at the right in the room of "100%", which returns the value after a click or drag. */
    int slider(String id, int x, int y, int w, int value, String valueText, String tipText) {
        int textW = width("100%") + 6, trackW = Math.max(10, w - textW);
        int ty = y + SLIDER_H / 2 - 1, knobX = x + (trackW - 4) * value / 100;
        fill(x, ty, trackW, 3, CARD);
        fill(x, ty, knobX - x + 2, 3, ACCENT);
        round(knobX, y + 1, 4, SLIDER_H - 2, dragging(id) || hover(x, y, trackW, SLIDER_H) ? ACCENT_HOVER : TEXT);
        text(valueText, x + trackW + 6, y + 2, MUTED);
        tip(x, y, w, SLIDER_H, tipText);
        float[] at = dragArea(id, x, y, trackW, SLIDER_H);
        return at == null ? value : Math.round(at[0] * 100);
    }

    /** A slider from {@code min} to {@code max} with the value and {@code unit} at the right, which returns the value after a click or drag. */
    int rangeSlider(String id, int x, int y, int w, int value, int min, int max, String unit, String tipText) {
        // The same text room for every range, so that sliders on rows below each other line up.
        int textW = Math.max(width("-180%"), Math.max(width(max + unit), width(min + unit))) + 6, trackW = Math.max(10, w - textW);
        int ty = y + SLIDER_H / 2 - 1, knobX = x + (trackW - 4) * (value - min) / Math.max(1, max - min);
        fill(x, ty, trackW, 3, CARD);
        fill(x, ty, knobX - x + 2, 3, ACCENT);
        round(knobX, y + 1, 4, SLIDER_H - 2, dragging(id) || hover(x, y, trackW, SLIDER_H) ? ACCENT_HOVER : TEXT);
        text(value + unit, x + trackW + 6, y + 2, MUTED);
        tip(x, y, w, SLIDER_H, tipText);
        float[] at = dragArea(id, x, y, trackW, SLIDER_H);
        return at == null ? value : min + Math.round(at[0] * (max - min));
    }

    /** A single-line text input that returns true when the text changed this frame. */
    boolean textField(String id, TextField field, int x, int y, int w, int h, String placeholder, String tipText) {
        boolean focused = id.equals(focus);
        if (focused) focusDrawn = true;
        boolean hovered = hover(x, y, w, h);
        round(x, y, w, h, focused ? ACCENT : hovered ? FAINT : BORDER);
        round(x + 1, y + 1, w - 2, h - 2, focused ? PANEL : CARD);
        tip(x, y, w, h, tipText);
        if (press(id, x, y, w, h)) {
            focus = id;
            focused = true;
            field.selectAll = false;
            field.cursor = field.text.length();
        }
        boolean changed = focused && edit(field);
        int tx = x + 4, ty = y + (h - LINE) / 2 + 1, room = w - 8;
        if (field.text.isEmpty() && !focused) {
            text(ellipsize(placeholder, room), tx, ty, FAINT);
            return changed;
        }
        String shown = field.text;
        int start = 0;
        while (width(shown.substring(start)) > room && start < field.cursor) start++;
        String visible = shown.substring(start);
        while (width(visible) > room && !visible.isEmpty()) visible = visible.substring(0, visible.length() - 1);
        if (focused && field.selectAll && !visible.isEmpty()) fill(tx - 1, ty - 1, width(visible) + 2, LINE + 1, 0xFF2F5FB3);
        text(visible, tx, ty, TEXT);
        if (focused && (now() / 500) % 2 == 0) {
            int cx = tx + width(shown.substring(start, Math.max(start, field.cursor)));
            fill(cx, ty - 1, 1, LINE + 1, TEXT);
        }
        return changed;
    }

    /** Gives this frame's keys and typing to the focused field, until Esc, Enter or Tab ends the input. */
    private boolean edit(TextField field) {
        boolean changed = false;
        for (Iterator<KeyPress> it = keys.iterator(); it.hasNext(); ) {
            KeyPress k = it.next();
            it.remove();
            if (k.key == UiKey.ESCAPE || k.key == UiKey.ENTER || k.key == UiKey.TAB) {
                focus = null;
                break;
            }
            if (k.ctrl && k.key == UiKey.V) changed |= field.insert(clipboard.get());
            else changed |= field.key(k.key, k.ctrl);
        }
        if (typed.length() > 0) {
            changed |= field.insert(typed.toString());
            typed.setLength(0);
        }
        return changed;
    }

    /** Scrolls a list with a scrollbar when its rows do not fit, and returns the first visible row. */
    int scroll(String id, int x, int y, int w, int h, int rows, int rowHeight) {
        int visible = Math.max(1, h / rowHeight);
        int max = Math.max(0, rows - visible);
        return scrollIndex(id, new Rect(x, y, w, h), max, 3, max > 0 ? h * visible / rows : h);
    }

    /**
     * Scrolls a list with a scrollbar at its right edge when {@code max} is above 0, and returns the first visible index.
     *
     * @param max the highest first index, at which the last entry is visible
     * @param step the entries that one wheel step moves
     * @param thumb the height of the scrollbar thumb before its minimum
     */
    int scrollIndex(String id, Rect area, int max, int step, int thumb) {
        Integer stored = scrolls.get(id);
        int first = stored == null ? 0 : stored;
        if (wheel != 0 && hover(area.x, area.y, area.w, area.h)) first -= wheel * step;
        if (max > 0) {
            int trackX = area.x + area.w - 3;
            String bar = id + ".bar";
            float[] grab = dragArea(bar, trackX - 2, area.y, 5, area.h);
            if (grab != null) first = Math.round(grab[1] * max);
            fill(trackX, area.y, 2, area.h, CARD);
            int th = Math.max(8, thumb);
            int ty = area.y + (area.h - th) * Math.max(0, Math.min(first, max)) / max;
            fill(trackX, ty, 2, th, dragging(bar) ? TEXT : MUTED);
        }
        first = Math.max(0, Math.min(first, max));
        scrolls.put(id, first);
        return first;
    }

    void resetScroll(String id) {
        scrolls.remove(id);
    }

    // ---------------------------------------------------------------- color math

    static int hsv(float h, float s, float v) {
        float r, g, b;
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        switch (i) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q;
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /** @return {hue, saturation, value} in [0, 1] */
    static float[] toHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) / 6f;
            else if (max == g) h = ((b - r) / d + 2) / 6f;
            else h = ((r - g) / d + 4) / 6f;
            if (h < 0) h += 1;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }
}
