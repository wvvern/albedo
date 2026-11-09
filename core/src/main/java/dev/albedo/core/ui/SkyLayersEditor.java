package dev.albedo.core.ui;

import dev.albedo.core.Colors;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.config.Transform;
import dev.albedo.core.sky.SkyBaker;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyPresets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The editor of the custom sky on the Sky page: a list of layers, a page to add one, and a page for each layer, over a preview. */
final class SkyLayersEditor {
    /** What the editor shows, which only {@link #go} changes. */
    enum Screen { LIST, ADD, LAYER }

    /** The two pages of a layer: its look, or its place on the sky. */
    enum LayerTab { LOOK, PLACE }

    /** What the preview of a layer page shows. */
    enum PreviewSource {
        /** The full sky. */
        RESULT,
        /** This layer alone, also a layer that is only a mask, over the layers below for a filter. */
        THIS_LAYER
    }

    /** The immutable state of the editor's navigation. */
    static final class SkyPage {
        static final SkyPage LIST = new SkyPage(Screen.LIST, 0, LayerTab.LOOK, PreviewSource.RESULT);

        final Screen screen;
        /** The id of the open layer on the layer screen. */
        final int layerId;
        final LayerTab tab;
        final PreviewSource source;

        private SkyPage(Screen screen, int layerId, LayerTab tab, PreviewSource source) {
            this.screen = screen;
            this.layerId = layerId;
            this.tab = tab;
            this.source = source;
        }

        SkyPage add() { return new SkyPage(Screen.ADD, 0, LayerTab.LOOK, PreviewSource.RESULT); }

        SkyPage layer(int id) { return new SkyPage(Screen.LAYER, id, LayerTab.LOOK, source); }

        SkyPage withTab(LayerTab t) { return new SkyPage(screen, layerId, t, source); }

        SkyPage withSource(PreviewSource s) { return new SkyPage(screen, layerId, tab, s); }
    }

    /** The size of the preview image, the same shape as the image in the world. */
    static final int PREVIEW_W = 512, PREVIEW_H = 256;
    /** The height of one control row. */
    private static final int ROW = 16;
    private static final int GAP = 6;
    /** A window lower than this shows no preview on a layer page, which has many rows. */
    private static final int LAYER_PREVIEW_MIN_H = 280;

    private final Ui ui;
    private final Runnable relayout;
    private final SkyBaker preview;
    private SkyPage page = SkyPage.LIST;
    private CanvasImage shown;
    private final TextField[] hex = {new TextField(9, c -> Character.digit(c, 16) >= 0 || c == '#'),
        new TextField(9, c -> Character.digit(c, 16) >= 0 || c == '#')};
    private int labelW;

    /**
     * @param relayout tells the screen that the layout changed, so that it draws the frame again
     * @param errors receives a failure of a preview bake
     */
    SkyLayersEditor(Ui ui, Runnable relayout, Consumer<Throwable> errors) {
        this.ui = ui;
        this.relayout = relayout;
        this.preview = new SkyBaker(PREVIEW_W, PREVIEW_H, errors);
    }

    /** The only way the page changes; a text field or scroll of the old page does not carry over. */
    private void go(SkyPage next) {
        page = next;
        ui.clearFocus();
        ui.resetScroll("sky.rows");
        relayout.run();
    }

    /** Goes back to the list from another screen, for Esc, and returns whether there was a screen to leave. */
    boolean back() {
        if (page.screen == Screen.LIST) return false;
        go(SkyPage.LIST);
        return true;
    }

    /** Shows the list again, for a different profile or category. */
    void reset() {
        page = SkyPage.LIST;
    }

    /**
     * Draws the editor in the rectangle from y to {@code bottom}.
     *
     * @param screenH the height of the whole window, which decides whether a layer page has room for the preview
     * @param set takes the changed design
     */
    void draw(int x, int y, int w, int bottom, int screenH, SkyDesign design, Consumer<SkyDesign> set) {
        labelW = 0;
        for (SkyLayer.Kind k : SkyLayer.Kind.values()) for (SkyLayer.Param p : k.params()) labelW = Math.max(labelW, ui.width(Labels.param(k, p)));
        for (String l : new String[]{"Blend", "Opacity", "Show", "Mask", "Turn", "Tilt", "Roll", "Size"}) labelW = Math.max(labelW, ui.width(l));
        labelW += 8;
        int index = design.indexOf(page.layerId);
        if (page.screen == Screen.LAYER && index < 0) {
            go(SkyPage.LIST); // the layer is gone: deleted, or a different profile is active
            return;
        }
        switch (page.screen) {
            case ADD: addScreen(x, y, w, bottom, design, set); break;
            case LAYER: layerScreen(x, y, w, bottom, screenH >= LAYER_PREVIEW_MIN_H, design, index, set); break;
            default: listScreen(x, y, w, bottom, design, set);
        }
    }

    // ---------------------------------------------------------------- preview

    /** The design the preview bakes: the full sky, or one layer alone, also a layer that is only a mask. */
    private SkyDesign previewDesign(SkyDesign design, int index) {
        if (page.screen != Screen.LAYER || page.source == PreviewSource.RESULT || index < 0) return design;
        SkyLayer layer = design.layers().get(index).withOutput(SkyLayer.Output.SHOW).withMask(SkyMask.NONE).withOpacity(100)
                .withBlend(SkyLayer.Blend.NORMAL);
        List<SkyLayer> layers = new ArrayList<SkyLayer>();
        if (layer.kind().role() == SkyLayer.Role.FILTER) {
            for (SkyLayer below : design.below(index)) if (below.output() == SkyLayer.Output.SHOW) layers.add(below);
        }
        layers.add(layer);
        return design.withLayers(layers);
    }

    /** The height a preview of width {@code w} takes, with its compass row. */
    private static int previewHeight(int w) {
        return Math.min(72, w / 4) + 10;
    }

    /** Draws the sky image with the horizon, the zenith, the compass and the sun path, and returns the height used. */
    private int preview(int x, int y, int w, SkyDesign design, int index) {
        preview.request(previewDesign(design, index));
        int ph = previewHeight(w) - 10;
        Rect strip = new Rect(x, y, w, ph);
        SkyImage img = preview.image();
        SkyDesign drawn = img == null ? design : img.design();
        if (img == null) {
            ui.fill(x, y, w, ph, Ui.PANEL);
            ui.textFit("Making the preview", x + 4, y + 4, w - 8, Ui.FAINT);
        } else {
            if (shown == null || shown.serial() != img.serial()) {
                int rows = SkyPreviewGeometry.rows(drawn, img.height());
                shown = new CanvasImage(Arrays.copyOf(img.argb(), rows * img.width()), img.width(), rows, img.serial());
            }
            ui.c.image("sky.preview", shown, x, y, w, ph);
        }
        int horizon = SkyPreviewGeometry.y(strip, drawn, 0);
        ui.fill(x, horizon, w, 1, 0x99FFFFFF);
        // The vanilla sun rises in the east, passes through the zenith and sets in the west.
        for (int az : new int[]{SkyPreviewGeometry.SUNRISE, SkyPreviewGeometry.SUNSET}) {
            int sx = SkyPreviewGeometry.x(strip, az);
            for (int dy = y; dy < horizon; dy += 4) ui.fill(sx, dy, 1, Math.min(2, horizon - dy), 0xCCFFD24D);
        }
        ui.fill(x, y, w, 1, 0x66FFFFFF);
        ui.textFit("Zenith", x + w - ui.width("Zenith") - 3, y + 2, w, 0xCCFFFFFF);
        ui.tip(x, y, w, ph, "Preview of the full sky, with the horizon (line) and the zenith (top). Yellow: the sun path. "
                + "The sky in the world changes when you push Apply or Done.");
        if (w >= 24) {
            for (int i = 0; i < SkyPreviewGeometry.COMPASS.length; i++) {
                int cx = SkyPreviewGeometry.x(strip, i * 90);
                String label = SkyPreviewGeometry.COMPASS[i];
                ui.fill(cx, y + ph, 1, 2, Ui.MUTED);
                ui.text(label, Math.max(x, Math.min(x + w - ui.width(label), cx - ui.width(label) / 2)), y + ph + 2, Ui.MUTED);
            }
        }
        return ph + 10;
    }

    // ---------------------------------------------------------------- list

    private void listScreen(int x, int y, int w, int bottom, SkyDesign design, Consumer<SkyDesign> set) {
        int cy = y;
        if (bottom - y >= previewHeight(w) + 3 * ROW + 40) cy += preview(x, y, w, design, -1) + GAP;
        String addLabel = "+ Add layer";
        int aw = ui.buttonWidth(addLabel);
        if (ui.button("sky.add", x, cy, aw, Ui.H, addLabel, Ui.Style.PRIMARY, !design.isFull(),
                design.isFull() ? "A sky has a maximum of " + SkyDesign.MAX_LAYERS + " layers" : "Add a layer at the top")) {
            go(page.add());
            return;
        }
        int px = x + w;
        for (Map.Entry<String, SkyDesign> e : reversed(SkyPresets.all())) {
            String name = Character.toUpperCase(e.getKey().charAt(0)) + e.getKey().substring(1);
            int bw = ui.buttonWidth(name);
            if (px - bw < x + aw + 4) break;
            px -= bw;
            if (ui.confirmButton("sky.preset." + e.getKey(), px, cy, bw, Ui.H, name, "Start from the " + name + " sky",
                    "Click again to replace all layers with the " + name + " sky")) {
                set.accept(e.getValue());
                return;
            }
            px -= 3;
        }
        cy += Ui.H + GAP;
        boolean celestial = design.celestial() == SkyDesign.Celestial.SHOW, night = design.light() == SkyDesign.Light.DAY_NIGHT;
        boolean sphere = design.extent() == SkyDesign.Extent.SPHERE;
        Switch[] switches = {
            new Switch("sky.celestial", "Show sun, moon and stars", "The game draws the sun, moon and stars over the layers", celestial,
                    design.withCelestial(celestial ? SkyDesign.Celestial.HIDE : SkyDesign.Celestial.SHOW)),
            new Switch("sky.night", "Darker at night", "The sky gets dark at night and in rain, as the vanilla sky does", night,
                    design.withLight(night ? SkyDesign.Light.FIXED : SkyDesign.Light.DAY_NIGHT)),
            new Switch("sky.extent", "Full sphere", "The layers go around the full sky, also below the horizon", sphere,
                    design.withExtent(sphere ? SkyDesign.Extent.HORIZON : SkyDesign.Extent.SPHERE))};
        cy = switches(x, cy, w, switches, set) + 2;
        List<SkyLayer> layers = design.layers();
        if (layers.isEmpty()) {
            ui.paragraph("No layers. The sky is black. Add a layer or select a sky above.", x, cy + 2, w, Ui.FAINT);
            return;
        }
        int h = bottom - cy, visible = Math.max(1, h / ROW);
        int first = ui.scroll("sky.rows", x, cy, w, h, layers.size(), ROW);
        int listW = layers.size() > visible ? w - 6 : w;
        // The top layer first, as it is drawn over the others.
        for (int r = first; r < Math.min(layers.size(), first + visible); r++) {
            if (layerRow(x, cy + (r - first) * ROW, listW, design, layers.size() - 1 - r, set)) return;
        }
    }

    /** One on-off row of the list screen and the design that it gives when the user flips it. */
    private static final class Switch {
        final String id, label, tip;
        final boolean on;
        final SkyDesign flipped;

        Switch(String id, String label, String tip, boolean on, SkyDesign flipped) {
            this.id = id;
            this.label = label;
            this.tip = tip;
            this.on = on;
            this.flipped = flipped;
        }
    }

    /** Draws the switches in two columns when the width permits, else in one, and returns the y below them. */
    private int switches(int x, int y, int w, Switch[] switches, Consumer<SkyDesign> set) {
        int columns = w >= 300 ? 2 : 1, cw = (w - GAP * (columns - 1)) / columns, rowH = 14;
        for (int i = 0; i < switches.length; i++) {
            Switch s = switches[i];
            int sx = x + i % columns * (cw + GAP), sy = y + i / columns * rowH;
            if (ui.toggleRow(s.id, sx - 4, sy, cw + 4, s.label, s.on, s.tip) != s.on) set.accept(s.flipped);
        }
        return y + (switches.length + columns - 1) / columns * rowH;
    }

    private static List<Map.Entry<String, SkyDesign>> reversed(Map<String, SkyDesign> map) {
        List<Map.Entry<String, SkyDesign>> out = new ArrayList<Map.Entry<String, SkyDesign>>(map.entrySet());
        Collections.reverse(out);
        return out;
    }

    /** The name of a layer: its kind and its place from the bottom. */
    private static String name(SkyDesign design, int index) {
        return Labels.kind(design.layers().get(index).kind()) + " " + (index + 1);
    }

    /** One row of the layer list, which returns true when a click changed the list or the page. */
    private boolean layerRow(int x, int y, int w, SkyDesign design, int index, Consumer<SkyDesign> set) {
        SkyLayer l = design.layers().get(index);
        int h = ROW - 2, bx = x + w - 50;
        ui.round(x, y, w, h, Ui.PANEL);
        if (ui.button("sky.layer." + l.id(), x, y, bx - x - 2, h, "", Ui.Style.GHOST, true, "Edit " + name(design, index))) {
            go(page.layer(l.id()));
            return true;
        }
        boolean mask = l.output() == SkyLayer.Output.MASK_ONLY;
        if (l.kind().role() == SkyLayer.Role.GENERATOR) ui.swatch(x + 4, y + 3, 8, 8, l.param(SkyLayer.Param.COLOR_1) | 0xFF000000);
        else ui.outline(x + 4, y + 3, 8, 8, Ui.MUTED);
        String title = name(design, index);
        int tw = Math.min(ui.width(title), (bx - x) / 2);
        ui.text(ui.ellipsize(title, tw), x + 16, y + 3, mask ? Ui.FAINT : Ui.TEXT);
        StringBuilder detail = new StringBuilder();
        if (mask) detail.append("mask");
        else detail.append(Labels.blend(l.blend())).append(' ').append(l.opacity()).append('%');
        if (!l.mask().isNone()) {
            int mi = design.indexOf(l.mask().layer());
            if (mi >= 0) detail.append(", masked by ").append(name(design, mi));
        }
        ui.text(ui.ellipsize(detail.toString(), bx - x - 26 - tw), x + 22 + tw, y + 3, mask ? Ui.FAINT : Ui.MUTED);
        SkyLayer upBlocker = design.moveBlocker(index, 1), downBlocker = design.moveBlocker(index, -1);
        boolean top = index == design.layers().size() - 1, low = index == 0;
        if (ui.button("sky.up." + l.id(), bx, y + 1, 15, 12, "^", Ui.Style.GHOST, !top && upBlocker == null,
                upBlocker != null ? blockTip(design, upBlocker) : "Move up. A higher layer is drawn over the lower ones.")) {
            set.accept(design.withMoved(index, 1));
            return true;
        }
        if (ui.button("sky.down." + l.id(), bx + 16, y + 1, 15, 12, "v", Ui.Style.GHOST, !low && downBlocker == null,
                downBlocker != null ? blockTip(design, downBlocker) : "Move down")) {
            set.accept(design.withMoved(index, -1));
            return true;
        }
        if (ui.confirmButton("sky.del." + l.id(), bx + 34, y + 1, 15, 12, "x", "Delete this layer",
                "Click again to delete " + name(design, index))) {
            set.accept(design.withoutLayer(index));
            return true;
        }
        return false;
    }

    private static String blockTip(SkyDesign design, SkyLayer blocker) {
        int i = design.indexOf(blocker.id());
        return "Cannot move: " + name(design, i) + " uses the layer below it as its mask. A mask must be lower.";
    }

    // ---------------------------------------------------------------- add

    private void addScreen(int x, int y, int w, int bottom, SkyDesign design, Consumer<SkyDesign> set) {
        if (ui.button("sky.back", x, y, 13, Ui.H, "<", Ui.Style.GHOST, true, "Back to the layers (Esc)")) {
            go(SkyPage.LIST);
            return;
        }
        ui.text("Add a layer", x + 16, y + 3, Ui.TEXT);
        int cy = y + Ui.H + GAP;
        for (SkyLayer.Role role : SkyLayer.Role.values()) {
            String heading = role == SkyLayer.Role.GENERATOR ? "Make" : "Change";
            ui.text(heading, x, cy + 3, Ui.MUTED);
            ui.tip(x, cy, labelW, Ui.H, role == SkyLayer.Role.GENERATOR ? "Layers that paint their own image"
                    : "Layers that change the layers below them");
            int bx = x + ui.width("Change") + 8, by = cy;
            for (SkyLayer.Kind k : SkyLayer.Kind.values()) {
                if (k.role() != role) continue;
                String label = Labels.kind(k);
                int bw = ui.buttonWidth(label);
                if (bx + bw > x + w && bx > x + ui.width("Change") + 8) {
                    bx = x + ui.width("Change") + 8;
                    by += Ui.H + 2;
                }
                if (by + Ui.H > bottom) break;
                if (ui.button("sky.kind." + k.key(), bx, by, bw, Ui.H, label, Ui.Style.NORMAL, !design.isFull(), Labels.kindTip(k))) {
                    int id = design.nextId();
                    set.accept(design.withAdded(k));
                    go(page.layer(id));
                    return;
                }
                bx += bw + 3;
            }
            cy = by + Ui.H + GAP;
        }
    }

    // ---------------------------------------------------------------- one layer

    /** Draws one row of a layer page at y. */
    private interface Row {
        void draw(int y);
    }

    private void layerScreen(int x, int y, int w, int bottom, boolean withPreview, SkyDesign design, int index, Consumer<SkyDesign> set) {
        SkyLayer layer = design.layers().get(index);
        if (ui.button("sky.back", x, y, 13, Ui.H, "<", Ui.Style.GHOST, true, "Back to the layers (Esc)")) {
            go(SkyPage.LIST);
            return;
        }
        String[] tabs = {"Look", "Place"};
        int tabW = ui.buttonWidth("Look") + ui.buttonWidth("Place");
        int tab = ui.segmented("sky.tab", x + w - tabW, y, tabW, tabs, page.tab.ordinal(),
                new String[]{"Colors, blend and mask", "Where the layer is on the sky"});
        if (tab != page.tab.ordinal()) {
            go(page.withTab(LayerTab.values()[tab]));
            return;
        }
        String title = name(design, index) + " of " + design.layers().size();
        ui.textFit(title, x + 16, y + 3, w - tabW - 20, Ui.TEXT);
        ui.tip(x + 16, y, w - tabW - 20, Ui.H, Labels.kindTip(layer.kind()));
        int cy = y + Ui.H + GAP;
        if (withPreview && bottom - cy >= previewHeight(w) + 4 * ROW) {
            String[] sources = {"All layers", "This layer"};
            int sw = ui.buttonWidth("All layers") + ui.buttonWidth("This layer");
            ui.text("Preview", x, cy + 3, Ui.MUTED);
            int s = ui.segmented("sky.source", x + labelW, cy, Math.min(sw, w - labelW), sources, page.source.ordinal(),
                    new String[]{"The full sky", "Only this layer, also when it is only a mask"});
            if (s != page.source.ordinal()) {
                go(page.withSource(PreviewSource.values()[s]));
                return;
            }
            cy += Ui.H + 2;
            cy += preview(x, cy, w, design, index) + GAP;
        }
        List<Row> rows = page.tab == LayerTab.LOOK ? lookRows(x, w, design, index, set) : placeRows(x, w, design, index, set);
        int h = bottom - cy, visible = Math.max(1, h / ROW);
        int first = ui.scroll("sky.rows", x, cy, w, h, rows.size(), ROW);
        for (int r = first; r < Math.min(rows.size(), first + visible); r++) rows.get(r).draw(cy + (r - first) * ROW);
    }

    private List<Row> lookRows(int x, int w0, SkyDesign design, int index, Consumer<SkyDesign> set) {
        SkyLayer l = design.layers().get(index);
        int w = w0 - 6; // room for the scroll bar
        int lx = x + labelW, cw = w - labelW;
        Consumer<SkyLayer> put = next -> set.accept(design.withLayer(index, next));
        List<Row> rows = new ArrayList<Row>();
        rows.add(y -> {
            String[] labels = new String[SkyLayer.Blend.values().length], tips = new String[labels.length];
            for (SkyLayer.Blend b : SkyLayer.Blend.values()) {
                labels[b.ordinal()] = Labels.blend(b);
                tips[b.ordinal()] = Labels.blendTip(b);
            }
            ui.text("Blend", x, y + 3, Ui.MUTED);
            int b = ui.segmented("sky.blend", lx, y, cw, labels, l.blend().ordinal(), tips);
            if (b != l.blend().ordinal()) put.accept(l.withBlend(SkyLayer.Blend.values()[b]));
        });
        rows.add(y -> {
            ui.text("Opacity", x, y + 2, Ui.MUTED);
            int o = ui.slider("sky.opacity", lx, y, cw, l.opacity(), "How much of this layer shows");
            if (o != l.opacity()) put.accept(l.withOpacity(o));
        });
        rows.add(y -> {
            ui.text("Show", x, y + 3, Ui.MUTED);
            int o = ui.segmented("sky.output", lx, y, cw, new String[]{"In the sky", "Mask only"}, l.output().ordinal(),
                    new String[]{"The layer shows in the sky", "The layer does not show. Higher layers can use it as their mask."});
            if (o != l.output().ordinal()) put.accept(l.withOutput(SkyLayer.Output.values()[o]));
        });
        rows.add(y -> maskRow(x, y, lx, cw, design, index, put));
        for (SkyLayer.Param p : l.kind().params()) rows.add(y -> paramRow(x, y, lx, cw, l, p, put));
        return rows;
    }

    /** The mask: a button that goes through None and each lower layer, then whether light or dark parts let the layer through. */
    private void maskRow(int x, int y, int lx, int cw, SkyDesign design, int index, Consumer<SkyLayer> put) {
        SkyLayer l = design.layers().get(index);
        ui.text("Mask", x, y + 3, Ui.MUTED);
        List<SkyLayer> below = design.below(index);
        int current = l.mask().isNone() ? -1 : design.indexOf(l.mask().layer());
        String label = current < 0 ? "None" : name(design, current);
        int bw = l.mask().isNone() ? cw : cw / 2 - 2;
        String tip = below.isEmpty() ? "The lowest layer has no layer below it to use as a mask"
                : "Show this layer only where a lower layer is light or dark. Click for the next layer.";
        if (ui.button("sky.mask", lx, y, bw, Ui.H, label + "  >", Ui.Style.NORMAL, !below.isEmpty(), tip)) {
            int next = current + 1;
            put.accept(next >= below.size() ? l.withMask(SkyMask.NONE)
                    : l.withMask(SkyMask.of(below.get(next).id(), l.mask().isNone() ? SkyMask.Mode.LIGHT : l.mask().mode())));
            return;
        }
        if (!l.mask().isNone()) {
            int m = ui.segmented("sky.maskmode", lx + bw + 4, y, cw - bw - 4, new String[]{"Light", "Dark"}, l.mask().mode().ordinal(),
                    new String[]{"Show where the mask layer is light", "Show where the mask layer is dark"});
            if (m != l.mask().mode().ordinal()) put.accept(l.withMask(l.mask().withMode(SkyMask.Mode.values()[m])));
        }
    }

    private void paramRow(int x, int y, int lx, int cw, SkyLayer l, SkyLayer.Param p, Consumer<SkyLayer> put) {
        String label = Labels.param(l.kind(), p);
        ui.text(label, x, y + 3, Ui.MUTED);
        int v = l.param(p);
        if (p.isColor()) {
            TextField field = hex[p == SkyLayer.Param.COLOR_1 ? 0 : 1];
            String id = "sky.hex." + p.key;
            ui.swatch(lx, y + 2, 10, 10, v);
            if (!ui.isFocused(id)) field.set(Colors.format(v));
            if (ui.textField(id, field, lx + 14, y, Math.min(62, cw - 14), Ui.H, "#RRGGBB", "Type a color: #RGB, #RRGGBB or #AARRGGBB")) {
                try {
                    put.accept(l.withParam(p, Colors.parse(field.text)));
                } catch (IllegalArgumentException ignored) {
                    // keep typing
                }
            }
        } else if (p == SkyLayer.Param.SEED) {
            String text = "Pattern " + v;
            ui.text(text, lx, y + 3, Ui.TEXT);
            int bw = ui.buttonWidth("New pattern");
            if (ui.button("sky.seed", lx + Math.max(ui.width(text) + 8, cw - bw), y, bw, Ui.H, "New pattern", Ui.Style.NORMAL, true,
                    "Make a different pattern of the same kind")) {
                put.accept(l.withParam(p, (v * 7919 + 17) % (p.max() + 1)));
            }
        } else {
            int n = ui.slider("sky.param." + p.key, lx, y, cw, v, label);
            if (n != v) put.accept(l.withParam(p, n));
        }
    }

    private List<Row> placeRows(int x, int w0, SkyDesign design, int index, Consumer<SkyDesign> set) {
        SkyLayer l = design.layers().get(index);
        int w = w0 - 6; // room for the scroll bar
        int lx = x + labelW, cw = w - labelW;
        List<Row> rows = new ArrayList<Row>();
        if (l.kind().axes().isEmpty()) {
            rows.add(y -> ui.textFirstFit(x, y + 3, w, Ui.FAINT, "This kind changes all of the sky. It has no place.", "No place"));
            return rows;
        }
        for (Transform.Axis a : Transform.Axis.values()) {
            if (!l.kind().axes().contains(a)) continue;
            rows.add(y -> {
                ui.text(Labels.axis(a), x, y + 2, Ui.MUTED);
                ui.tip(x, y, labelW, Ui.SLIDER_H, Labels.axisTip(a));
                int v = l.transform().get(a);
                int n = ui.rangeSlider("sky.place." + a.name(), lx, y, cw, v, Transform.min(a), Transform.max(a),
                        a == Transform.Axis.SCALE ? "%" : "", Labels.axisTip(a));
                if (n != v) set.accept(design.withLayer(index, l.withTransform(l.transform().with(a, n))));
            });
        }
        rows.add(y -> {
            int bw = ui.buttonWidth("Reset");
            boolean changed = !l.transform().equals(l.kind().defaultTransform());
            if (ui.button("sky.place.reset", lx, y, bw, Ui.H, "Reset", Ui.Style.NORMAL, changed, "Put the layer back in its first place")) {
                set.accept(design.withLayer(index, l.withTransform(l.kind().defaultTransform())));
            }
        });
        return rows;
    }
}
