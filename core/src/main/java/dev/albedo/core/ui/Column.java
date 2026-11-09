package dev.albedo.core.ui;

import java.util.List;
import java.util.function.IntUnaryOperator;

/** A vertical list of items of different heights, which scrolls by whole items because the canvas cannot clip. */
final class Column {
    private Column() {}

    /** One entry of a column, such as a labelled row, a paragraph or a section heading. */
    interface Item {
        /** The height of the item at width {@code w}, the gap below it included. */
        int height(int w);

        /** Draws the item and returns true when it changed which items the column holds, so that the column stops drawing. */
        boolean draw(int x, int y, int w);
    }

    /** Draws an item and returns true when it changed which items the column holds. */
    interface Draw {
        boolean draw(int x, int y, int w);
    }

    /** An item from its height and its drawing. */
    static Item of(IntUnaryOperator height, Draw draw) {
        return new Item() {
            @Override public int height(int w) { return height.applyAsInt(w); }
            @Override public boolean draw(int x, int y, int w) { return draw.draw(x, y, w); }
        };
    }

    /** An item of one height at all widths. */
    static Item of(int height, Draw draw) {
        return of(w -> height, draw);
    }

    /**
     * Draws the items from the scrolled first one while they fit in {@code area}, with a scrollbar when they do not
     * all fit; one wheel step moves one item.
     *
     * @return the bottom of the last item drawn
     */
    static int draw(Ui ui, String id, Rect area, List<Item> items) {
        int w = area.w;
        int[] heights = heights(items, w);
        if (sum(heights, 0) > area.h) {
            w -= 6; // room for the scrollbar
            heights = heights(items, w);
        }
        int total = sum(heights, 0), max = 0;
        // The highest first item is the one from which all the rest fit.
        while (max < items.size() - 1 && sum(heights, max) > area.h) max++;
        int first = ui.scrollIndex(id, area, max, 1, area.h * area.h / Math.max(1, total));
        int y = area.y;
        for (int i = first; i < items.size() && y + heights[i] <= area.bottom(); i++) {
            if (items.get(i).draw(area.x, y, w)) return y + heights[i];
            y += heights[i];
        }
        return y;
    }

    /** Draws all the items one below the other with no scrolling, and returns the bottom of the last item drawn. */
    static int stack(int x, int y, int w, List<Item> items) {
        for (Item item : items) {
            int h = item.height(w);
            if (item.draw(x, y, w)) return y + h;
            y += h;
        }
        return y;
    }

    private static int[] heights(List<Item> items, int w) {
        int[] out = new int[items.size()];
        for (int i = 0; i < out.length; i++) out[i] = items.get(i).height(w);
        return out;
    }

    private static int sum(int[] heights, int from) {
        int s = 0;
        for (int i = from; i < heights.length; i++) s += heights[i];
        return s;
    }
}
