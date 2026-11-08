package dev.worldpainter.core.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A tiny immediate-mode GUI toolkit. Widgets are plain method calls made while drawing each frame; each call draws
 * the widget and returns what the user did to it since the last frame. Input events are queued by the adapter between
 * frames. Keeping all of this in the core means the screen looks and behaves the same on every Minecraft version.
 *
 * <p>Layout rules shared by all widgets: controls are {@link #H} pixels high, text is vertically centered in them,
 * labels that do not fit are shortened with "..." and the full text shows in the tooltip.
 */
final class Ui {
    // Theme
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
    /** Time in ms a pointer must rest on a widget before its tooltip shows. 0 in tests. */
    int tipDelay = 400;
    /** Least space around a label inside a segment. */
    private static final int SEG_PAD = 6;
    /** Time in ms a delete button stays armed after the first click. */
    private static final int CONFIRM_MS = 3000;

    enum Style { NORMAL, PRIMARY, DANGER, GHOST }

    private static final class KeyPress {
        final UiKey key;
        final boolean ctrl;

        KeyPress(UiKey key, boolean ctrl) {
            this.key = key;
            this.ctrl = ctrl;
        }
    }

    Canvas c;
    int mx = -1, my = -1;
    private boolean down;
    private boolean pressed;
    private int wheel;
    private String drag;
    private String focus;
    private String hint;
    private boolean hintNow;
    private String shownHint;
    private long hintSince;
    private boolean tipAfterClick;
    private String armed;
    private long armedAt;
    /** Height used by the last {@link #segmented} call (it wraps into rows when the labels do not fit). */
    int lastHeight;
    private final List<KeyPress> keys = new ArrayList<KeyPress>();
    private final StringBuilder typed = new StringBuilder();
    private final Map<String, Integer> scrolls = new HashMap<String, Integer>();
    private final Clipboard clipboard;

    interface Clipboard {
        String get();
    }

    Ui(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    // ---------------------------------------------------------------- events (between frames; the pointer position
    // comes from each frame's render call)

    void mousePressed() {
        down = true;
        pressed = true;
    }

    void mouseReleased() {
        down = false;
        drag = null;
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

    /** True when the left button went down since the last frame (before any widget takes the press). */
    boolean clicked() { return clickedFrame; }

    private boolean clickedFrame;

    void begin(Canvas canvas, int mouseX, int mouseY) {
        this.c = canvas;
        clickedFrame = pressed;
        if (mouseX != mx || mouseY != my) {
            hintSince = System.currentTimeMillis();
            tipAfterClick = false;
        }
        mx = mouseX;
        my = mouseY;
        hint = null;
        hintNow = false;
        if (armed != null && System.currentTimeMillis() - armedAt > CONFIRM_MS) armed = null;
    }

    /** A press nobody claimed clears focus and disarms delete buttons; unclaimed typing is dropped. Draws the tooltip. */
    void end() {
        endOverlay();
        if (pressed) {
            focus = null;
            armed = null;
        }
        pressed = false;
        wheel = 0;
        typed.setLength(0);
        keys.clear();
        tooltip();
    }

    boolean isFocused(String id) { return id.equals(focus); }

    boolean hasFocus() { return focus != null; }

    void clearFocus() { focus = null; }

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

    /** Esc first disarms an armed delete button. */
    boolean disarm() {
        if (armed == null) return false;
        armed = null;
        return true;
    }

    boolean hover(int x, int y, int w, int h) {
        if (overlay != null && !inOverlay && inside(overlay, mx, my)) return false;
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ---------------------------------------------------------------- overlay (dropdowns drawn over other widgets)

    private int[] overlay;
    private boolean inOverlay;

    /** Widgets drawn until {@link #beginOverlay()} ignore the pointer inside this rectangle. */
    void reserveOverlay(int x, int y, int w, int h) {
        overlay = new int[]{x, y, w, h};
    }

    /** Widgets drawn from now on are the overlay itself. */
    void beginOverlay() {
        inOverlay = true;
    }

    void endOverlay() {
        overlay = null;
        inOverlay = false;
    }

    private static boolean inside(int[] r, int x, int y) {
        return x >= r[0] && y >= r[1] && x < r[0] + r[2] && y < r[1] + r[3];
    }

    void focus(String id) {
        focus = id;
    }

    private boolean press(String id, int x, int y, int w, int h) {
        if (!pressed || !hover(x, y, w, h)) return false;
        pressed = false;
        drag = id;
        tipAfterClick = true;
        if (!id.equals(focus)) focus = null;
        if (!id.equals(armed)) armed = null;
        return true;
    }

    /** Sets the tooltip for this frame when the pointer is on the rectangle. */
    void tip(int x, int y, int w, int h, String text) {
        if (text != null && hover(x, y, w, h)) hint = text;
    }

    /** A tooltip near the pointer, after the pointer rests for {@link #tipDelay} ms; wrapped and kept on screen. */
    private void tooltip() {
        if (hint == null) {
            shownHint = null;
            return;
        }
        if (!hint.equals(shownHint)) {
            shownHint = hint;
            hintSince = System.currentTimeMillis();
        }
        // After a click the result matters more than the explanation: no tooltip until the pointer moves.
        if (!hintNow && (tipAfterClick || System.currentTimeMillis() - hintSince < tipDelay)) return;
        int maxW = Math.min(170, c.width() - 8);
        List<String> lines = wrap(hint, maxW);
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, width(l));
        int bw = tw + 8, bh = lines.size() * (LINE + 1) + 5;
        int x = Math.max(2, Math.min(mx + 6, c.width() - bw - 2));
        int y = my + 12;
        if (y + bh > c.height() - 2) y = Math.max(2, my - bh - 4);
        round(x, y, bw, bh, BORDER);
        round(x + 1, y + 1, bw - 2, bh - 2, TOOLTIP);
        for (int i = 0; i < lines.size(); i++) text(lines.get(i), x + 4, y + 3 + i * (LINE + 1), TEXT);
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

    int width(String s) { return c.textWidth(s); }

    /** The width of a button that shows all of {@code label}. */
    int buttonWidth(String label) { return width(label) + 12; }

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

    /**
     * A delete button that needs two clicks: the first arms it (it turns red and says so), the second within a few
     * seconds returns true. A click anywhere else or Esc disarms it.
     */
    boolean confirmButton(String id, int x, int y, int w, int h, String label, String tipText, String confirmTip) {
        if (!id.equals(armed)) {
            if (button(id, x, y, w, h, label, Style.DANGER, true, tipText)) {
                armed = id;
                armedAt = System.currentTimeMillis();
            }
            return false;
        }
        round(x, y, w, h, hover(x, y, w, h) ? 0xFFF07C83 : DANGER);
        textCentered(ellipsize(label, w - 4), x + w / 2, y + (h - LINE) / 2 + 1, 0xFFFFFFFF);
        if (hover(x, y, w, h)) {
            hint = confirmTip;
            hintNow = true;
        }
        if (press(id, x, y, w, h)) {
            armed = null;
            return true;
        }
        return false;
    }

    boolean isArmed(String id) { return id.equals(armed); }

    /**
     * A removable tag: swatch, label and an x. Returns its width; {@code removed[0]} is set when the x was clicked.
     * {@code dim} draws it faded (for entries that currently have no effect).
     */
    int chipWidth(String label, boolean hasSwatch) {
        return 4 + (hasSwatch ? 10 : 0) + width(label) + 12;
    }

    int chip(String id, int x, int y, Integer swatch, String label, boolean dim, String tipText, boolean[] removed) {
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
        if (button(id + ".x", x + w - 11, y + 1, 10, 10, "x", Style.GHOST, true, "Remove " + label)) removed[0] = true;
        return w;
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
     * Page tabs: labels in a row with an accent line under the selected one, on a full-width rule. They look
     * different from {@link #segmented} on purpose: tabs change the page, segmented controls change a value.
     */
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
            int fg = i == selected ? TEXT : hovered ? TEXT : MUTED;
            text(shown, tx + (fits ? 0 : (tw - width(shown)) / 2), y + 2, fg);
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

    /**
     * Segments sized to their labels, sharing the spare width equally; returns the selected index. When the labels
     * do not fit in one row the control wraps into rows of equal count; {@link #lastHeight} holds the height used.
     */
    int segmented(String id, int x, int y, int w, int h, String[] labels, int selected, String[] tips) {
        int n = labels.length, rows = 1;
        while (rows < n && !rowsFit(labels, rows, w)) rows++;
        int per = (n + rows - 1) / rows;
        lastHeight = rows * h + (rows - 1) * 2;
        int result = selected;
        for (int r = 0; r < rows; r++) {
            int from = r * per, to = Math.min(n, from + per), ry = y + r * (h + 2);
            int need = 0;
            for (int i = from; i < to; i++) need += width(labels[i]) + SEG_PAD;
            int spare = Math.max(0, w - need), sx = x;
            round(x, ry, w, h, CARD);
            for (int i = from; i < to; i++) {
                int k = i - from, count = to - from;
                int sw = width(labels[i]) + SEG_PAD + spare * (k + 1) / count - spare * k / count;
                if (i == to - 1) sw = x + w - sx;
                boolean hovered = hover(sx, ry, sw, h);
                if (i == selected) round(sx + 1, ry + 1, sw - 2, h - 2, ACCENT);
                else if (hovered) round(sx + 1, ry + 1, sw - 2, h - 2, CARD_HOVER);
                String shown = ellipsize(labels[i], sw - 2);
                textCentered(shown, sx + sw / 2, ry + (h - LINE) / 2 + 1, i == selected ? 0xFFFFFFFF : hovered ? TEXT : MUTED);
                tip(sx, ry, sw, h, tips[i]);
                if (press(id + "." + i, sx, ry, sw, h)) result = i;
                sx += sw;
            }
        }
        return result;
    }

    private boolean rowsFit(String[] labels, int rows, int w) {
        int per = (labels.length + rows - 1) / rows;
        for (int from = 0; from < labels.length; from += per) {
            int need = 0;
            for (int i = from; i < Math.min(labels.length, from + per); i++) need += width(labels[i]) + SEG_PAD;
            if (need > w) return false;
        }
        return true;
    }

    /**
     * A draggable area; returns the pointer position inside it as fractions {x, y} in [0, 1] while it is pressed or
     * dragged, otherwise null.
     */
    float[] dragArea(String id, int x, int y, int w, int h) {
        if (!press(id, x, y, w, h) && !(id.equals(drag) && down)) return null;
        float fx = Math.max(0f, Math.min(1f, (mx - x) / (float) Math.max(1, w - 1)));
        float fy = Math.max(0f, Math.min(1f, (my - y) / (float) Math.max(1, h - 1)));
        return new float[]{fx, fy};
    }

    /** Single-line text input. Returns true when the text changed this frame. */
    boolean textField(String id, TextField field, int x, int y, int w, int h, String placeholder, String tipText) {
        boolean focused = id.equals(focus);
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
        boolean changed = false;
        if (focused) {
            for (Iterator<KeyPress> it = keys.iterator(); it.hasNext(); ) {
                KeyPress k = it.next();
                if (k.key == UiKey.ESCAPE || k.key == UiKey.ENTER || k.key == UiKey.TAB) {
                    focus = null;
                    it.remove();
                    break;
                }
                if (k.ctrl && k.key == UiKey.V) changed |= field.insert(clipboard.get());
                else changed |= field.key(k.key, k.ctrl);
                it.remove();
            }
            if (typed.length() > 0) {
                changed |= field.insert(typed.toString());
                typed.setLength(0);
            }
        }
        int tx = x + 4, ty = y + (h - LINE) / 2 + 1, room = w - 8;
        if (field.text.isEmpty() && !focused) {
            text(ellipsize(placeholder, room), tx, ty, FAINT);
        } else {
            String shown = field.text;
            int start = 0;
            while (width(shown.substring(start)) > room && start < field.cursor) start++;
            String visible = shown.substring(start);
            while (width(visible) > room && !visible.isEmpty()) visible = visible.substring(0, visible.length() - 1);
            if (focused && field.selectAll && !visible.isEmpty()) fill(tx - 1, ty - 1, width(visible) + 2, LINE + 1, 0xFF2F5FB3);
            text(visible, tx, ty, TEXT);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = tx + width(shown.substring(start, Math.max(start, field.cursor)));
                fill(cx, ty - 1, 1, LINE + 1, TEXT);
            }
        }
        return changed;
    }

    /**
     * Vertical list scrolling. Returns the first visible row; draws a scrollbar when the rows don't fit.
     */
    int scroll(String id, int x, int y, int w, int h, int rows, int rowHeight) {
        int visible = Math.max(1, h / rowHeight);
        int max = Math.max(0, rows - visible);
        Integer stored = scrolls.get(id);
        int first = stored == null ? 0 : stored;
        if (wheel != 0 && hover(x, y, w, h)) first -= wheel * 3;
        if (max > 0) {
            int trackX = x + w - 3;
            float[] grab = dragArea(id + ".bar", trackX - 2, y, 5, h);
            if (grab != null) first = Math.round(grab[1] * max);
            fill(trackX, y, 2, h, CARD);
            int thumb = Math.max(8, h * visible / rows);
            int ty = y + (h - thumb) * Math.max(0, Math.min(first, max)) / max;
            fill(trackX, ty, 2, thumb, (id + ".bar").equals(drag) ? TEXT : MUTED);
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
