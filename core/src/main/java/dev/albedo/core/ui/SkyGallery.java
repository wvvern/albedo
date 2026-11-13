package dev.albedo.core.ui;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.sky.SkyBaker;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyPresets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The cards of the built-in skies in the sky editor: a grid that scrolls by rows, where each card shows a small bake of its sky. */
final class SkyGallery {
    static final String SCROLL = "sky.gallery";
    /** The size of the bake of a card image, the same shape as the image in the world. */
    private static final int BAKE_W = 256, BAKE_H = 128;
    /** The part of the bake from the top that a card shows: from the zenith down to a little below the horizon. */
    private static final float IMAGE_PART = (SkyImage.TOP - SkyImage.layersBottom(SkyDesign.Extent.HORIZON)) / (SkyImage.TOP - SkyImage.BOTTOM);
    /** The least width of a card, which gives the number of columns up to {@link #MAX_COLUMNS}, so that a wide window has large images. */
    private static final int MIN_CARD_W = 100, MAX_COLUMNS = 4;
    /** The least width of a card in a window too narrow for two cards of {@link #MIN_CARD_W}. */
    private static final int NARROW_CARD_W = 88;
    private static final int GAP = 4, PAD = 2;
    /** The lines of the description on a card with room for them. */
    private static final int LINES = 2;
    /** The height of a card that shows only its name. */
    private static final int NAME_ONLY_H = Ui.H;
    /** A card image lower than this shows too little, so the card has only its name then. */
    private static final int MIN_IMAGE_H = 12;

    private final Ui ui;
    private final Consumer<Throwable> errors;
    /** The running bake of each card that was drawn and has no image yet, by preset key. */
    private final Map<String, SkyBaker> bakers = new HashMap<String, SkyBaker>();
    private final Map<String, CanvasImage> images = new HashMap<String, CanvasImage>();

    /** The measures of the cards for one width and height of the grid. */
    private static final class Layout {
        final int columns, cardW, imageH, lines, cardH;

        /**
         * Shows as many rows as the height permits: with the description, else without it, else with a lower image, which
         * shows the same sky pressed flat, and in a window too low for one such card with what is left for the image.
         *
         * @param rows the rows of all cards
         */
        Layout(int w, int h, int rows) {
            columns = columns(w);
            cardW = (w - (columns - 1) * GAP) / columns;
            int full = imageHeight(cardW), bestRows = 0, bestImage = Math.min(full, h - cardHeight(0, 0)), bestLines = 0;
            for (int lower = 0; lower <= 1; lower++) {
                for (int l = LINES; l >= 0; l -= LINES) {
                    int image = full;
                    if (lower == 1) {
                        // One more row than fits with the full image, when the image keeps two thirds of its height.
                        int fit = (h + GAP) / (cardHeight(full, l) + GAP);
                        image = Math.min(full, (h + GAP) / (fit + 1) - GAP - cardHeight(0, l));
                        if (image < full * 2 / 3) continue;
                    }
                    int shown = Math.min(rows, (h + GAP) / (cardHeight(image, l) + GAP));
                    if (shown > bestRows) {
                        bestRows = shown;
                        bestImage = image;
                        bestLines = l;
                    }
                }
            }
            imageH = bestImage;
            lines = bestLines;
            cardH = imageH < MIN_IMAGE_H ? NAME_ONLY_H : cardHeight(imageH, lines);
        }

        boolean hasImage() { return cardH != NAME_ONLY_H; }
    }

    /** @param errors receives a failure of the bake of a card image */
    SkyGallery(Ui ui, Consumer<Throwable> errors) {
        this.ui = ui;
        this.errors = errors;
    }

    private static int columns(int w) {
        int columns = Math.min(MAX_COLUMNS, (w + GAP) / (MIN_CARD_W + GAP));
        // A narrow window has two narrow cards in a row and not one wide card, so that it shows more skies.
        return columns >= 2 ? columns : w >= 2 * NARROW_CARD_W + GAP ? 2 : 1;
    }

    /** The height of the image of a card of width {@code cardW} that shows the sky in its own shape. */
    private static int imageHeight(int cardW) {
        return Math.round((cardW - 2 * PAD) * IMAGE_PART * BAKE_H / BAKE_W);
    }

    /** The height of a card with an image of {@code imageH} over its name and {@code lines} lines of description. */
    private static int cardHeight(int imageH, int lines) {
        return PAD + imageH + 3 + Ui.LINE + (lines > 0 ? 2 + lines * (Ui.LINE + 1) - 1 : 0) + PAD + 1;
    }

    /**
     * The height that {@code rows} rows of cards with a full image and no description take in a grid of width {@code w}, or all
     * rows when there are fewer.
     */
    static int height(int w, int rows) {
        int columns = columns(w), all = (SkyPresets.all().size() + columns - 1) / columns, count = Math.min(rows, all);
        int cardW = (w - (columns - 1) * GAP) / columns;
        return count * (cardHeight(imageHeight(cardW), 0) + GAP) - GAP;
    }

    /**
     * Draws the cards in the rectangle and returns the key of the card that was clicked, or null.
     *
     * @param chosen the key of the marked card, or null
     */
    String draw(int x, int y, int w, int h, String chosen) {
        if (h < NAME_ONLY_H) return null;
        List<SkyPresets.Preset> presets = SkyPresets.all();
        int rows = (presets.size() + columns(w) - 1) / columns(w);
        Layout l = new Layout(w, h, rows);
        int pitch = l.cardH + GAP;
        int visible = Math.max(1, (h + GAP) / pitch);
        int first = ui.scrollIndex(SCROLL, new Rect(x, y, w, h), Math.max(0, rows - visible), 1, h * visible / rows);
        int cardW = rows > visible ? (w - 6 - (l.columns - 1) * GAP) / l.columns : l.cardW;
        String clicked = null;
        for (int r = first; r < Math.min(rows, first + visible); r++) {
            for (int c = 0; c < l.columns && r * l.columns + c < presets.size(); c++) {
                SkyPresets.Preset p = presets.get(r * l.columns + c);
                if (card(p, x + c * (cardW + GAP), y + (r - first) * pitch, cardW, l, p.key().equals(chosen))) clicked = p.key();
            }
        }
        return clicked;
    }

    private boolean card(SkyPresets.Preset p, int x, int y, int w, Layout l, boolean chosen) {
        boolean hovered = ui.hover(x, y, w, l.cardH);
        ui.panel(new Rect(x, y, w, l.cardH), chosen ? Ui.ACCENT : hovered ? Ui.BORDER : Ui.PANEL, hovered ? Ui.CARD_HOVER : Ui.PANEL);
        int tx = x + PAD + 2, tw = w - 2 * (PAD + 2);
        if (!l.hasImage()) {
            ui.text(ui.ellipsize(p.name(), tw), tx, y + (l.cardH - Ui.LINE) / 2 + 1, Ui.TEXT);
            ui.tip(x, y, w, l.cardH, p.description());
            return ui.press("sky.gallery." + p.key(), x, y, w, l.cardH);
        }
        CanvasImage img = image(p);
        if (img == null) ui.fill(x + PAD, y + PAD, w - 2 * PAD, l.imageH, Ui.CARD);
        else ui.c.image("sky.gallery." + p.key(), img, x + PAD, y + PAD, w - 2 * PAD, l.imageH);
        int ty = y + PAD + l.imageH + 3;
        ui.text(ui.ellipsize(p.name(), tw), tx, ty, Ui.TEXT);
        List<String> text = ui.wrap(p.description(), tw);
        boolean cut = text.size() > l.lines;
        for (int i = 0; i < Math.min(l.lines, text.size()); i++) {
            String line = cut && i == l.lines - 1 ? ui.ellipsize(text.get(i) + " " + text.get(i + 1), tw) : text.get(i);
            ui.text(line, tx, ty + Ui.LINE + 2 + i * (Ui.LINE + 1), Ui.MUTED);
        }
        if (cut || tw < ui.width(p.name())) ui.tip(x, y, w, l.cardH, p.description());
        return ui.press("sky.gallery." + p.key(), x, y, w, l.cardH);
    }

    /** The image of a card, or null while its bake runs. */
    private CanvasImage image(SkyPresets.Preset p) {
        CanvasImage done = images.get(p.key());
        if (done != null) return done;
        SkyBaker baker = bakers.get(p.key());
        if (baker == null) {
            baker = new SkyBaker(BAKE_W, BAKE_H, errors);
            bakers.put(p.key(), baker);
        }
        baker.request(p.design());
        SkyImage img = baker.image();
        if (img == null) return null;
        int w = img.width(), rows = Math.max(1, Math.round(IMAGE_PART * img.height()));
        // The bake has north at its left edge, and the card has the direction of the preset in its middle.
        int shift = Math.floorMod(Math.round(p.facing() / 360f * w) - w / 2, w);
        int[] src = img.argb(), argb = new int[rows * w];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < w; x++) argb[y * w + x] = src[y * w + (x + shift) % w];
        }
        // A preset never changes, so one serial serves the image for as long as the screen is open.
        done = new CanvasImage(argb, w, rows, 1);
        images.put(p.key(), done);
        bakers.remove(p.key());
        return done;
    }
}
