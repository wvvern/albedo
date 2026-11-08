package dev.albedo.core.ui;

/** An immutable screen rectangle in GUI pixels. */
final class Rect {
    final int x, y, w, h;

    Rect(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    boolean contains(int px, int py) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }

    int bottom() { return y + h; }
}
