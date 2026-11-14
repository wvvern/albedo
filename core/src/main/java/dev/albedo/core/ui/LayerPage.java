package dev.albedo.core.ui;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.config.Transform;
import dev.albedo.core.ui.SkyLayersEditor.LayerTab;
import dev.albedo.core.ui.SkyLayersEditor.SkyPage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The page of one sky layer: its look or its place, over a preview of the sky or of the layer alone. */
final class LayerPage {
    private final Ui ui;
    private final SkyPreview preview;
    /** One color input for each color parameter, so that each keeps its own typed text. */
    private final Map<SkyLayer.Param, ColorField> colors = new EnumMap<SkyLayer.Param, ColorField>(SkyLayer.Param.class);

    LayerPage(Ui ui, SkyPreview preview) {
        this.ui = ui;
        this.preview = preview;
        for (SkyLayer.Param p : SkyLayer.Param.values()) if (p.isColor()) colors.put(p, new ColorField(ui));
    }

    /**
     * Draws the page of the layer at {@code index} and returns the page to go to, or null to stay.
     *
     * @param withPreview whether the window is high enough for the preview
     * @param labelW the width of the labels of the rows
     */
    SkyPage draw(int x, int y, int w, int bottom, boolean withPreview, SkyPage page, int labelW, SkyDesign design, int index,
            Consumer<SkyDesign> set) {
        SkyLayer layer = design.layers().get(index);
        if (ui.backButton("sky.back", x, y, "Back to the layers (Esc)")) return SkyPage.LIST;
        String[] tabs = {"Look", "Place"};
        int tabW = ui.buttonWidth("Look") + ui.buttonWidth("Place");
        int tab = ui.segmented("sky.tab", x + w - tabW, y, tabW, tabs, page.tab.ordinal(),
                new String[]{"Colors, blend and mask", "Where the layer is on the sky"});
        if (tab != page.tab.ordinal()) return page.withTab(LayerTab.values()[tab]);
        String title = Labels.layer(design, index) + " of " + design.layers().size();
        ui.textFit(title, x + 16, y + 3, w - tabW - 20, Ui.TEXT);
        ui.tip(x + 16, y, w - tabW - 20, Ui.H, Labels.kindTip(layer.kind()));
        int cy = y + Ui.H + SkyLayersEditor.GAP;
        if (withPreview && bottom - cy >= SkyPreview.height(w) + 4 * SkyLayersEditor.ROW) {
            Form form = new Form(ui, x, w, labelW);
            int sw = 0;
            for (SkyPreviewSource s : SkyPreviewSource.values()) sw += ui.buttonWidth(Labels.source(s));
            form.label(cy, Ui.H, "Preview", null);
            SkyPreviewSource s = ui.choice("sky.source", form.lx(), cy, Math.min(sw, form.cw()), SkyPreviewSource.values(), page.source,
                    Labels::source, Labels::sourceTip);
            if (s != page.source) return page.withSource(s);
            cy += Ui.H + 2;
            cy += preview.draw(x, cy, w, design, index, page.source) + SkyLayersEditor.GAP;
        }
        List<RowList.Line> rows = page.tab == LayerTab.LOOK ? lookRows(x, w, labelW, design, index, set)
                : placeRows(x, w, labelW, design, index, set);
        RowList.draw(ui, "sky.rows", x, cy, w, bottom - cy, rows.size(), SkyLayersEditor.ROW, (r, rx, ry, rw, rh) -> {
            rows.get(r).draw(ry);
            return false;
        });
        return null;
    }

    private List<RowList.Line> lookRows(int x, int w0, int labelW, SkyDesign design, int index, Consumer<SkyDesign> set) {
        SkyLayer l = design.layers().get(index);
        Form form = new Form(ui, x, w0 - 6, labelW); // room for the scroll bar
        Consumer<SkyLayer> put = next -> set.accept(design.withLayer(index, next));
        List<RowList.Line> rows = new ArrayList<RowList.Line>();
        rows.add(y -> {
            form.label(y, Ui.H, "Blend", null);
            SkyLayer.Blend b = ui.choice("sky.blend", form.lx(), y, form.cw(), SkyLayer.Blend.values(), l.blend(),
                    Labels::blend, Labels::blendTip);
            if (b != l.blend()) put.accept(l.withBlend(b));
        });
        rows.add(y -> {
            form.label(y, Ui.SLIDER_H, "Opacity", null);
            int o = ui.slider("sky.opacity", form.lx(), y, form.cw(), l.opacity(), "How much of this layer shows");
            if (o != l.opacity()) put.accept(l.withOpacity(o));
        });
        rows.add(y -> {
            form.label(y, Ui.H, "Show", null);
            SkyLayer.Output o = ui.choice("sky.output", form.lx(), y, form.cw(), SkyLayer.Output.values(), l.output(),
                    Labels::output, Labels::outputTip);
            if (o != l.output()) put.accept(l.withOutput(o));
        });
        rows.add(y -> maskRow(form, y, design, index, put));
        for (SkyLayer.Param p : l.kind().params()) rows.add(y -> paramRow(form, y, l, p, put));
        return rows;
    }

    /** The mask: a button that goes through None and each lower layer, then whether light or dark parts let the layer through. */
    private void maskRow(Form form, int y, SkyDesign design, int index, Consumer<SkyLayer> put) {
        SkyLayer l = design.layers().get(index);
        int lx = form.lx(), cw = form.cw();
        form.label(y, Ui.H, "Mask", null);
        List<SkyLayer> below = design.below(index);
        int current = l.mask().isNone() ? -1 : design.indexOf(l.mask().layer());
        String label = current < 0 ? "None" : Labels.layer(design, current);
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
            SkyMask.Mode m = ui.choice("sky.maskmode", lx + bw + 4, y, cw - bw - 4, SkyMask.Mode.values(), l.mask().mode(),
                    Labels::maskMode, Labels::maskModeTip);
            if (m != l.mask().mode()) put.accept(l.withMask(l.mask().withMode(m)));
        }
    }

    private void paramRow(Form form, int y, SkyLayer l, SkyLayer.Param p, Consumer<SkyLayer> put) {
        String label = Labels.param(l.kind(), p);
        int lx = form.lx(), cw = form.cw();
        form.label(y, Ui.H, label, null);
        int v = l.param(p);
        if (p.isColor()) {
            int n = colors.get(p).draw("sky.hex." + p.key, lx, y, cw, v);
            if (n != v) put.accept(l.withParam(p, n));
        } else if (p == SkyLayer.Param.SEED) {
            String text = "Pattern " + v;
            ui.text(text, lx, y + 3, Ui.TEXT);
            int bw = ui.buttonWidth("New pattern");
            if (ui.button("sky.seed", lx + Math.max(ui.width(text) + 8, cw - bw), y, bw, Ui.H, "New pattern", Ui.Style.NORMAL, true,
                    "Make a different pattern of the same kind")) {
                put.accept(l.withParam(p, (v * 7919 + 17) % (p.max() + 1)));
            }
        } else if (p == SkyLayer.Param.TILT) {
            // The percent slider of the other rows, so that the tracks line up, with the value in degrees.
            int at = ui.slider("sky.param." + p.key, lx, y, cw, Math.round(v * 100f / p.max()), String.valueOf(v),
                    "0 shows the disc from the front, 90 from its edge");
            int n = Math.round(at * p.max() / 100f);
            if (n != v) put.accept(l.withParam(p, n));
        } else {
            int n = ui.slider("sky.param." + p.key, lx, y, cw, v, label);
            if (n != v) put.accept(l.withParam(p, n));
        }
    }

    private List<RowList.Line> placeRows(int x, int w0, int labelW, SkyDesign design, int index, Consumer<SkyDesign> set) {
        SkyLayer l = design.layers().get(index);
        int w = w0 - 6; // room for the scroll bar
        Form form = new Form(ui, x, w, labelW);
        List<RowList.Line> rows = new ArrayList<RowList.Line>();
        if (l.kind().axes().isEmpty()) {
            rows.add(y -> ui.textFirstFit(x, y + 3, w, Ui.FAINT, "This kind changes all of the sky. It has no place.", "No place"));
            return rows;
        }
        for (Transform.Axis a : Transform.Axis.values()) {
            if (!l.kind().axes().contains(a)) continue;
            rows.add(y -> {
                int v = l.transform().get(a);
                int n = form.range("sky.place." + a.name(), y, Labels.axis(a), Labels.axisTip(a), v, Transform.min(a), Transform.max(a),
                        a == Transform.Axis.SCALE ? "%" : "");
                if (n != v) set.accept(design.withLayer(index, l.withTransform(l.transform().with(a, n))));
            });
        }
        rows.add(y -> {
            int bw = ui.buttonWidth("Reset");
            boolean changed = !l.transform().equals(l.kind().defaultTransform());
            if (ui.button("sky.place.reset", form.lx(), y, bw, Ui.H, "Reset", Ui.Style.NORMAL, changed, "Put the layer back in its first place")) {
                set.accept(design.withLayer(index, l.withTransform(l.kind().defaultTransform())));
            }
        });
        return rows;
    }
}
