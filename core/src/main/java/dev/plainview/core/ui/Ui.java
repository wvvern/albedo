package dev.plainview.core.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A tiny immediate-mode GUI toolkit. Widgets are plain method calls made while drawing each frame; each call draws
 * the widget and returns what the user did to it since the last frame. Input events are queued by the adapter between
 * frames. Keeping all of this in the core means the screen looks and behaves the same on every Minecraft version.
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
    static final int MUTED = 0xFF8B94A5;
    static final int FAINT = 0xFF5D6575;
    static final int ACCENT = 0xFF4D8EFF;
    static final int ACCENT_HOVER = 0xFF6FA3FF;
    static final int DANGER = 0xFFE5636B;
    static final int LINE = 9;

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

    void begin(Canvas canvas, int mouseX, int mouseY) {
        this.c = canvas;
        mx = mouseX;
        my = mouseY;
        hint = null;
    }

    /** A press nobody claimed clears focus; unclaimed typing is dropped. */
    void end() {
        endOverlay();
        if (pressed) focus = null;
        pressed = false;
        wheel = 0;
        typed.setLength(0);
        keys.clear();
    }

    String hint() { return hint; }

    boolean isFocused(String id) { return id.equals(focus); }

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
        if (!id.equals(focus)) focus = null;
        return true;
    }

    private void tip(int x, int y, int w, int h, String text) {
        if (text != null && hover(x, y, w, h)) hint = text;
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

    int width(String s) { return c.textWidth(s); }

    /** Draws text wrapped at word boundaries within {@code w}; returns the height used. */
    int paragraph(String s, int x, int y, int w, int color) {
        int lineY = y;
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (width(next) > w && line.length() > 0) {
                text(ellipsize(line.toString(), w), x, lineY, color);
                lineY += LINE + 1;
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        if (line.length() > 0) text(ellipsize(line.toString(), w), x, lineY, color);
        return lineY + LINE - y;
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
        int fg = !enabled ? FAINT : style == Style.DANGER ? DANGER : TEXT;
        textCentered(ellipsize(label, w - 4), x + w / 2, y + (h - LINE) / 2 + 1, fg);
        tip(x, y, w, h, tipText);
        return enabled && press(id, x, y, w, h);
    }

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
        if (hover(x, y, w, h)) round(x, y, w, h, CARD);
        text(label, x + 4, y + 2, TEXT);
        boolean flipped = toggle(id + ".switch", x + w - 22, y + 1, on, tipText) != on;
        tip(x, y, w, h, tipText);
        return (flipped || press(id, x, y, w, h)) != on;
    }

    /** Equal-width segments; returns the selected index. */
    int segmented(String id, int x, int y, int w, int h, String[] labels, int selected, String[] tips) {
        round(x, y, w, h, CARD);
        int n = labels.length;
        int result = selected;
        for (int i = 0; i < n; i++) {
            int sx = x + w * i / n, sw = x + w * (i + 1) / n - sx;
            boolean hovered = hover(sx, y, sw, h);
            if (i == selected) round(sx + 1, y + 1, sw - 2, h - 2, ACCENT);
            else if (hovered) round(sx + 1, y + 1, sw - 2, h - 2, CARD_HOVER);
            textCentered(ellipsize(labels[i], sw - 2), sx + sw / 2, y + (h - LINE) / 2 + 1, i == selected ? 0xFFFFFFFF : hovered ? TEXT : MUTED);
            tip(sx, y, sw, h, tips[i]);
            if (press(id + "." + i, sx, y, sw, h)) result = i;
        }
        return result;
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
        round(x, y, w, h, focused ? ACCENT : BORDER);
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
            fill(trackX, ty, 2, thumb, MUTED);
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
