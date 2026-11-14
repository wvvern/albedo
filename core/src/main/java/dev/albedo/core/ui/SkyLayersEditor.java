package dev.albedo.core.ui;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.config.Transform;
import dev.albedo.core.sky.SkyPresets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The editor of the custom sky on the Sky page: a list of layers, a page to add one, a page with the built-in skies, and a page
 * for each layer, over a preview.
 */
final class SkyLayersEditor {
    /** What the editor shows; {@link #go} and {@link #reset} change it. */
    enum Screen { LIST, ADD, GALLERY, LAYER }

    /** The two pages of a layer: its look, or its place on the sky. */
    enum LayerTab { LOOK, PLACE }

    /** The immutable state of the editor's navigation. */
    static final class SkyPage {
        static final SkyPage LIST = new SkyPage(Screen.LIST, 0, LayerTab.LOOK, SkyPreviewSource.RESULT, null);

        final Screen screen;
        /** The id of the open layer on the layer screen. */
        final int layerId;
        final LayerTab tab;
        final SkyPreviewSource source;
        /** The key of the chosen card on the gallery screen, or null. */
        final String preset;

        private SkyPage(Screen screen, int layerId, LayerTab tab, SkyPreviewSource source, String preset) {
            this.screen = screen;
            this.layerId = layerId;
            this.tab = tab;
            this.source = source;
            this.preset = preset;
        }

        static SkyPage add() { return new SkyPage(Screen.ADD, 0, LayerTab.LOOK, SkyPreviewSource.RESULT, null); }

        static SkyPage gallery(String chosen) { return new SkyPage(Screen.GALLERY, 0, LayerTab.LOOK, SkyPreviewSource.RESULT, chosen); }

        SkyPage layer(int id) { return new SkyPage(Screen.LAYER, id, LayerTab.LOOK, source, null); }

        SkyPage withTab(LayerTab t) { return new SkyPage(screen, layerId, t, source, null); }

        SkyPage withSource(SkyPreviewSource s) { return new SkyPage(screen, layerId, tab, s, null); }
    }

    /** The height of one control row. */
    static final int ROW = 16;
    static final int GAP = 6;
    /** A window lower than this shows no preview on a layer page, which has many rows. */
    private static final int LAYER_PREVIEW_MIN_H = 280;
    private static final String SKIES = "Skies";

    private final Ui ui;
    private final Runnable relayout;
    private final SkyPreview preview;
    private final SkyGallery gallery;
    private final MotionSection motion;
    private final LayerPage layerPage;
    private SkyPage page = SkyPage.LIST;
    private int labelW;

    /**
     * @param relayout tells the screen that the layout changed, so that it draws the frame again
     * @param errors receives a failure of a preview bake
     */
    SkyLayersEditor(Ui ui, Runnable relayout, Consumer<Throwable> errors) {
        this.ui = ui;
        this.relayout = relayout;
        this.preview = new SkyPreview(ui, errors);
        this.gallery = new SkyGallery(ui, errors);
        this.motion = new MotionSection(ui, relayout);
        this.layerPage = new LayerPage(ui, preview);
    }

    /** Changes the page; the focus and scroll of the old page do not carry over. */
    private void go(SkyPage next) {
        // A click on a card keeps the place of the cards.
        if (next.screen != page.screen) ui.resetScroll(SkyGallery.SCROLL);
        page = next;
        ui.clearFocus();
        ui.resetScroll("sky.rows");
        relayout.run();
    }

    /** Goes back to the list from another screen, for Esc, and returns whether there was a screen to leave. */
    boolean back() {
        if (page.screen == Screen.LIST) return false;
        // The preview of a chosen card that was not used must not show as the sky of the list while the bake runs.
        if (page.screen == Screen.GALLERY && page.preset != null) preview.clear();
        go(SkyPage.LIST);
        return true;
    }

    /** Shows the list again, for a different profile, category or style. */
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
        for (String l : new String[]{"Blend", "Opacity", "Show", "Mask"}) labelW = Math.max(labelW, ui.width(l));
        for (Transform.Axis a : Transform.Axis.values()) labelW = Math.max(labelW, ui.width(Labels.axis(a)));
        labelW += 8;
        int index = design.indexOf(page.layerId);
        if (page.screen == Screen.LAYER && index < 0) {
            go(SkyPage.LIST); // the layer is gone: deleted, or a different profile is active
            return;
        }
        switch (page.screen) {
            case ADD: addScreen(x, y, w, bottom, design, set); break;
            case GALLERY: galleryScreen(x, y, w, bottom, design, set); break;
            case LAYER:
                SkyPage next = layerPage.draw(x, y, w, bottom, screenH >= LAYER_PREVIEW_MIN_H, page, labelW, design, index, set);
                if (next != null) go(next);
                break;
            default: listScreen(x, y, w, bottom, design, set);
        }
    }

    private void listScreen(int x, int y, int w, int bottom, SkyDesign design, Consumer<SkyDesign> set) {
        int cy = y;
        if (bottom - y >= SkyPreview.height(w) + 3 * ROW + 40 + motion.height(design)) {
            cy += preview.draw(x, y, w, design, -1, SkyPreviewSource.RESULT) + GAP;
        }
        String addLabel = "+ Add layer";
        int aw = ui.buttonWidth(addLabel);
        if (ui.button("sky.add", x, cy, aw, Ui.H, addLabel, Ui.Style.PRIMARY, !design.isFull(),
                design.isFull() ? "A sky has a maximum of " + SkyDesign.MAX_LAYERS + " layers" : "Add a layer at the top")) {
            go(SkyPage.add());
            return;
        }
        int sw = ui.buttonWidth(SKIES);
        if (ui.button("sky.skies", x + w - sw, cy, sw, Ui.H, SKIES, Ui.Style.NORMAL, true, "Start from a ready-made sky")) {
            go(SkyPage.gallery(null));
            return;
        }
        cy += Ui.H + GAP;
        boolean celestial = design.celestial() == SkyDesign.Celestial.SHOW, night = design.light() == SkyDesign.Light.DAY_NIGHT;
        boolean sphere = design.extent() == SkyDesign.Extent.SPHERE, sharp = design.render() == SkyDesign.Render.SHARP;
        Switch[] switches = {
            new Switch("sky.celestial", "Show sun, moon and stars", "The game draws the sun, moon and stars over the layers", celestial,
                    design.withCelestial(celestial ? SkyDesign.Celestial.HIDE : SkyDesign.Celestial.SHOW)),
            new Switch("sky.night", "Darker at night", "The sky gets dark at night and in rain, as the vanilla sky does", night,
                    design.withLight(night ? SkyDesign.Light.FIXED : SkyDesign.Light.DAY_NIGHT)),
            design.turning().turns()
                    ? new Switch("sky.extent", "Full sphere",
                            "A sky that turns always goes around the full sky. Set Turning to 0 to stop the layers at the horizon.", true, null)
                    : new Switch("sky.extent", "Full sphere", "The layers go around the full sky, also below the horizon", sphere,
                            design.withExtent(sphere ? SkyDesign.Extent.HORIZON : SkyDesign.Extent.SPHERE)),
            new Switch("sky.render", "Sharp render", "The GPU draws the sky sharp at all screen sizes. Set it off if the game is slow.", sharp,
                    design.withRender(sharp ? SkyDesign.Render.FAST : SkyDesign.Render.SHARP))};
        cy = switches(x, cy, w, switches, set) + 2;
        cy = motion.draw(x, cy, w, bottom, design, set);
        List<SkyLayer> layers = design.layers();
        if (layers.isEmpty()) {
            ui.paragraph("No layers. The sky is black. Add a layer or click " + SKIES + ".", x, cy + 2, w, Ui.FAINT);
            return;
        }
        if (bottom - cy < ROW) return; // the open Motion section takes the room of the list
        // The top layer first, as it is drawn over the others.
        RowList.draw(ui, "sky.rows", x, cy, w, bottom - cy, layers.size(), ROW,
                (r, rx, ry, rw, rh) -> layerRow(rx, ry, rw, design, layers.size() - 1 - r, set));
    }

    /** One on-off row of the list screen and the design that it gives when the user flips it, or null when it is locked. */
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
            if (s.flipped == null) ui.lockedToggleRow(sx - 4, sy, cw + 4, s.label, s.on, s.tip);
            else if (ui.toggleRow(s.id, sx - 4, sy, cw + 4, s.label, s.on, s.tip) != s.on) set.accept(s.flipped);
        }
        return y + (switches.length + columns - 1) / columns * rowH;
    }

    /** One row of the layer list, which returns true when a click changed the list or the page. */
    private boolean layerRow(int x, int y, int w, SkyDesign design, int index, Consumer<SkyDesign> set) {
        SkyLayer l = design.layers().get(index);
        int h = ROW - 2, bx = x + w - RowButtons.W;
        ui.round(x, y, w, h, Ui.PANEL);
        String title = Labels.layer(design, index);
        if (ui.button("sky.layer." + l.id(), x, y, bx - x - 2, h, "", Ui.Style.GHOST, true, "Edit " + title)) {
            go(page.layer(l.id()));
            return true;
        }
        boolean mask = l.output() == SkyLayer.Output.MASK_ONLY;
        if (l.kind().role() == SkyLayer.Role.GENERATOR) ui.swatch(x + 4, y + 3, 8, 8, l.param(SkyLayer.Param.COLOR_1) | 0xFF000000);
        else ui.outline(x + 4, y + 3, 8, 8, Ui.MUTED);
        int tw = Math.min(ui.width(title), (bx - x) / 2);
        ui.text(ui.ellipsize(title, tw), x + 16, y + 3, mask ? Ui.FAINT : Ui.TEXT);
        StringBuilder detail = new StringBuilder();
        if (mask) detail.append("mask");
        else detail.append(Labels.blend(l.blend())).append(' ').append(l.opacity()).append('%');
        if (!l.mask().isNone()) {
            int mi = design.indexOf(l.mask().layer());
            if (mi >= 0) detail.append(", masked by ").append(Labels.layer(design, mi));
        }
        ui.text(ui.ellipsize(detail.toString(), bx - x - 26 - tw), x + 22 + tw, y + 3, mask ? Ui.FAINT : Ui.MUTED);
        SkyLayer upBlocker = design.moveBlocker(index, 1), downBlocker = design.moveBlocker(index, -1);
        boolean top = index == design.layers().size() - 1, low = index == 0;
        switch (RowButtons.draw(ui, "sky", String.valueOf(l.id()), bx, y, !top && upBlocker == null, !low && downBlocker == null,
                upBlocker != null ? blockTip(design, upBlocker) : "Move up. A higher layer is drawn over the lower ones.",
                downBlocker != null ? blockTip(design, downBlocker) : "Move down", "Delete this layer", "Click again to delete " + title)) {
            case UP: set.accept(design.withMoved(index, 1)); return true;
            case DOWN: set.accept(design.withMoved(index, -1)); return true;
            case DELETE: set.accept(design.withoutLayer(index)); return true;
            default: return false;
        }
    }

    private static String blockTip(SkyDesign design, SkyLayer blocker) {
        int i = design.indexOf(blocker.id());
        return "Cannot move: " + Labels.layer(design, i) + " uses the layer below it as its mask. A mask must be lower.";
    }

    /** The preset of the chosen card, or null when no card is chosen. */
    private SkyPresets.Preset chosen() {
        for (SkyPresets.Preset p : SkyPresets.all()) if (p.key().equals(page.preset)) return p;
        return null;
    }

    /** The design that a preset gives: the Motion settings stay, except for a preset with its own turning, such as Trails. */
    private static SkyDesign started(SkyPresets.Preset p, SkyDesign from) {
        return p.design().turning().equals(SkyTurning.DEFAULT) ? p.design().withTurning(from.turning()) : p.design();
    }

    /** The cards of the built-in skies, where a click chooses one for the preview and only the Use button changes the layers. */
    private void galleryScreen(int x, int y, int w, int bottom, SkyDesign design, Consumer<SkyDesign> set) {
        if (ui.backButton("sky.back", x, y, "Back to the layers (Esc)")) {
            back();
            return;
        }
        SkyPresets.Preset chosen = chosen();
        String use = "Use this sky";
        int uw = ui.buttonWidth(use);
        if (ui.button("sky.use", x + w - uw, y, uw, Ui.H, use, Ui.Style.PRIMARY, chosen != null,
                chosen == null ? "Click a sky first" : "Replace all layers with the " + Labels.presetName(chosen.key()) + " sky")) {
            set.accept(started(chosen, design));
            go(SkyPage.LIST);
            return;
        }
        ui.textFit("Start from a sky", x + 16, y + 3, w - uw - 20, Ui.TEXT);
        int cy = y + Ui.H + GAP;
        if (bottom - cy >= SkyPreview.height(w) + GAP + SkyGallery.height(w, 2)) {
            cy += preview.draw(x, cy, w, chosen == null ? design : started(chosen, design), -1, SkyPreviewSource.RESULT) + GAP;
        }
        String clicked = gallery.draw(x, cy, w, bottom - cy, page.preset);
        if (clicked != null && !clicked.equals(page.preset)) go(SkyPage.gallery(clicked));
    }

    private void addScreen(int x, int y, int w, int bottom, SkyDesign design, Consumer<SkyDesign> set) {
        if (ui.backButton("sky.back", x, y, "Back to the layers (Esc)")) {
            go(SkyPage.LIST);
            return;
        }
        ui.text("Add a layer", x + 16, y + 3, Ui.TEXT);
        int cy = y + Ui.H + GAP, rowH = Ui.H + 2;
        // The lines scroll when they do not all fit, and then the scroll bar takes some of their width.
        List<List<SkyLayer.Kind>> fit = kindLines(w);
        List<List<SkyLayer.Kind>> lines = fit.size() * rowH > bottom - cy ? kindLines(w - 6) : fit;
        RowList.draw(ui, "sky.kinds", x, cy, w, bottom - cy, lines.size(), rowH, (r, rx, ry, rw, rh) -> {
            List<SkyLayer.Kind> line = lines.get(r);
            SkyLayer.Role role = line.get(0).role();
            if (r == 0 || lines.get(r - 1).get(0).role() != role) {
                ui.text(role == SkyLayer.Role.GENERATOR ? "Make" : "Change", rx, ry + 3, Ui.MUTED);
                ui.tip(rx, ry, labelW, Ui.H, role == SkyLayer.Role.GENERATOR ? "Layers that paint their own image"
                        : "Layers that change the layers below them");
            }
            int bx = rx + ui.width("Change") + 8;
            for (SkyLayer.Kind k : line) {
                String label = Labels.kind(k);
                int bw = ui.buttonWidth(label);
                if (ui.button("sky.kind." + k.key(), bx, ry, bw, Ui.H, label, Ui.Style.NORMAL, !design.isFull(), Labels.kindTip(k))) {
                    int id = design.nextId();
                    set.accept(design.withAdded(k));
                    go(page.layer(id));
                    return true;
                }
                bx += bw + 3;
            }
            return false;
        });
    }

    /** The kinds as lines of buttons that fit a width, where each role starts a new line that has its heading on the left. */
    private List<List<SkyLayer.Kind>> kindLines(int w) {
        List<List<SkyLayer.Kind>> lines = new ArrayList<List<SkyLayer.Kind>>();
        int left = ui.width("Change") + 8;
        for (SkyLayer.Role role : SkyLayer.Role.values()) {
            List<SkyLayer.Kind> line = new ArrayList<SkyLayer.Kind>();
            int bx = left;
            for (SkyLayer.Kind k : SkyLayer.Kind.values()) {
                if (k.role() != role) continue;
                int bw = ui.buttonWidth(Labels.kind(k));
                if (bx + bw > w && !line.isEmpty()) {
                    lines.add(line);
                    line = new ArrayList<SkyLayer.Kind>();
                    bx = left;
                }
                line.add(k);
                bx += bw + 3;
            }
            if (!line.isEmpty()) lines.add(line);
        }
        return lines;
    }
}
