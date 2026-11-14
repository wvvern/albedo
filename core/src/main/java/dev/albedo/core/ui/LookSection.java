package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.config.Rule;
import dev.albedo.core.sky.SkyPresets;
import dev.albedo.core.texture.PixelTransformer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The rows of a look: the Look tab of a category, and the look of a group. */
final class LookSection {
    /** The labels left of controls; the label column is as wide as the widest. */
    private static final String[] ROW_LABELS = {"Style", "Color", "Shape", "Scope", "Recolor"};
    private static final String EFFECTS = "effects";
    private static final String[] CHROMA_IDS = {"effects.chromaSpeed", "effects.chromaSaturation", "effects.chromaBrightness"};
    private static final int TINT_ROW_H = 12;
    /** The height of a closed section heading. */
    private static final int SECTION_H = Ui.H + Ui.GAP;
    private static final List<String> MEMBERS = new ArrayList<String>();
    static {
        for (Category c : SettingsEditor.CATEGORIES) MEMBERS.add(Labels.members(c));
    }

    private final Ui ui;
    private final Albedo albedo;
    private final DraftSession session;
    private final SkyLayersEditor skyLayers;
    private final ColorPicker picker;
    /** The Effects section, open or closed for all categories and groups. */
    private final Section effects;
    /** Width of the label column ("Style", "Color", ...) plus its gap; set each frame from the font. */
    private int labelW;

    /** @param relayout tells the screen that the rows moved */
    LookSection(Ui ui, Albedo albedo, DraftSession session, SkyLayersEditor skyLayers, Runnable relayout) {
        this.ui = ui;
        this.albedo = albedo;
        this.session = session;
        this.skyLayers = skyLayers;
        this.picker = new ColorPicker(ui);
        this.effects = new Section(ui, EFFECTS, relayout);
    }

    /** Sets the label column from the font, the same for every category, so that the controls do not move when the category changes. */
    void measure() {
        int rows = Math.max(ui.widest(MEMBERS), Math.max(ui.widest(Arrays.asList(ROW_LABELS)), ui.widest(Arrays.asList(Labels.RECOLOR_LABELS))));
        int effectRows = Math.max(ui.width(Labels.CHROMA_LABEL), ui.widest(Arrays.asList(Labels.EFFECT_LABELS))) + Form.INDENT;
        int chromaRows = ui.widest(Arrays.asList(Labels.CHROMA_LABELS)) + 2 * Form.INDENT;
        labelW = Math.max(rows, Math.max(effectRows, chromaRows)) + 8;
    }

    /** A form with the label column of the page. */
    Form form(int x, int w) {
        return new Form(ui, x, w, labelW);
    }

    /** An 8x8 icon for a look: its color, or an empty outline for vanilla. */
    static void icon(Ui ui, int x, int y, Appearance a) {
        if (a.isOriginal()) ui.outline(x, y, 8, 8, Ui.FAINT);
        else ui.swatch(x, y, 8, 8, chipColor(a, 0xFF808080, null));
    }

    /** The real transform on one pixel of the {@code original} average color with the Chroma hue of {@code id}, or null for hidden. */
    static Integer chipColor(Appearance a, int original, String id) {
        return a.mode() == Appearance.Mode.HIDDEN ? null : PixelTransformer.preview(a, original, id);
    }

    /** The Look tab of {@code category} from y to {@code bottom}. */
    void tab(int x, int y, int w, int bottom, Category category, CategorySettings cs) {
        if (category == Category.SKY && cs.appearance().mode() == Appearance.Mode.LAYERS) {
            // The layer editor scrolls its own list, so the look rows above it do not scroll.
            int cy = Column.stack(x, y, w, items(category, cs, -1, w, bottom - y, 0));
            CategorySettings now = session.profile().get(category);
            if (now.appearance().mode() == Appearance.Mode.LAYERS) {
                skyLayers.draw(x, cy, w, bottom, ui.screenHeight(), now.sky(),
                        d -> session.setCategory(session.profile().get(Category.SKY).withSky(d)));
            }
            return;
        }
        List<Column.Item> after = tabItems(category, cs);
        int afterH = 0;
        for (Column.Item item : after) afterH += item.height(w);
        List<Column.Item> items = items(category, cs, -1, w, bottom - y, afterH);
        items.addAll(after);
        // The tint hint is last and not in the budget of the color picker, so a short window scrolls to it.
        boolean tint = category == Category.BLOCKS || category == Category.FLUIDS;
        Appearance look = cs.appearance();
        // Grass, leaves and water get the biome color on top of a recolor or Chroma, which mixes it with the chosen color.
        boolean mixes = look.mode() == Appearance.Mode.RECOLOR || look.changesPixels() && look.chroma().isOn();
        if (tint && !cs.removeTint() && mixes) {
            items.add(note("Grass, leaves and water also get the biome color. Turn on Remove biome tint to see only this color."));
        }
        Column.draw(ui, "look", new Rect(x, y, w, bottom - y), items);
    }

    /** The rows under the global look: the only-groups note, the biome tint switch and the sky fog note. */
    private List<Column.Item> tabItems(Category category, CategorySettings cs) {
        List<Column.Item> items = new ArrayList<Column.Item>();
        if (cs.onlyGroups()) items.add(note("Only groups change (see Groups). Groups set to Global use this look."));
        if (category == Category.BLOCKS || category == Category.FLUIDS) {
            items.add(Column.of(TINT_ROW_H, (x, y, w) -> {
                boolean remove = ui.toggleRow("tint", x - 4, y, w + 4, "Remove biome tint", cs.removeTint(),
                        category == Category.BLOCKS ? "Grass and leaves do not get the biome color" : "Water does not get the biome color");
                if (remove == cs.removeTint()) return false;
                session.setCategory(cs.withRemoveTint(remove));
                return true; // the hint under the switch depends on it
            }));
        }
        if (category == Category.SKY && cs.appearance().mode() == Appearance.Mode.SOLID) {
            items.add(note("This also sets the fog to the sky color."));
        }
        return items;
    }

    /** A paragraph of faint text, as a column item. */
    private Column.Item note(String text) {
        return Column.of(w -> ui.paragraphHeight(text, w) + Ui.GAP, (x, y, w) -> {
            ui.paragraph(text, x, y, w, Ui.FAINT);
            return false;
        });
    }

    /**
     * The rows of the global look or a group's look: style, color, recolor, shape and the Effects section.
     *
     * @param index the group, or -1 for the global look
     * @param w the width of the column, which the picker size is estimated for
     * @param viewH the height of the column, which the closed rows fit in when they can
     * @param after the height of the rows that the page puts under these
     */
    List<Column.Item> items(Category category, CategorySettings cs, int index, int w, int viewH, int after) {
        Rule rule = index < 0 ? null : cs.rules().get(index);
        boolean global = rule != null && rule.usesGlobalLook();
        Appearance target = cs.lookOf(rule);
        List<Column.Item> items = new ArrayList<Column.Item>();
        Column.Item style = styleItem(category, cs, index, rule, target, global);
        items.add(style);
        if (global) {
            items.add(globalItem(category, cs));
            return items;
        }
        Appearance.Mode m = target.mode();
        boolean usesShape = target.changesPixels() && category != Category.SKY && category != Category.CLOUDS;
        boolean recolor = m == Appearance.Mode.RECOLOR;
        boolean withEffects = target.changesPixels() && (albedo.chromaCanMove(category) || category.hasOutline());
        int fixed = style.height(w) + after + (withEffects ? SECTION_H : 0) + (usesShape ? shapeHeight(w) : 0);
        LookBudget budget = new LookBudget(viewH, fixed, recolor);
        if (m == Appearance.Mode.SOLID || recolor) items.add(colorItem(cs, index, target, recolor, budget));
        if (recolor) items.add(recolorItem(cs, index, target, budget));
        if (usesShape) items.addAll(shapeItems(category, cs, index, target, w, budget));
        if (withEffects) items.addAll(effectsSection(category, cs, index, target));
        return items;
    }

    /** The Style row, where the first style of a group is "Global" in the only-groups scope. */
    private Column.Item styleItem(Category category, CategorySettings cs, int index, Rule rule, Appearance target, boolean global) {
        List<Appearance.Mode> modes = category.supportedModes();
        String[] labels = new String[modes.size()], tips = new String[modes.size()];
        int current = 0;
        for (int i = 0; i < modes.size(); i++) {
            labels[i] = Labels.mode(modes.get(i));
            tips[i] = Labels.modeTip(modes.get(i));
            if (!global && modes.get(i) == target.mode()) current = i;
        }
        if (rule != null && cs.onlyGroups()) {
            labels[0] = "Global";
            tips[0] = "Use the " + Labels.name(category) + " look";
        } else if (rule != null) {
            tips[0] = "Do not change these textures";
        }
        int selected = current;
        return Column.of(iw -> ui.segmentedHeight(labels, iw - labelW) + Ui.GAP, (x, y, iw) -> {
            Form form = form(x, iw);
            form.label(y, Ui.H, "Style", null);
            int chosen = ui.segmented("mode", form.lx(), y, form.cw(), labels, selected, tips);
            if (chosen == selected) return false;
            if (rule != null && cs.onlyGroups() && chosen == 0) session.setRule(cs, index, rule.withGlobalLook());
            else if (modes.get(chosen) == Appearance.Mode.LAYERS && cs.sky().isEmpty()) {
                // A new custom sky starts from a preset, so that the first look is a sky and not black.
                session.setCategory(cs.withAppearance(target.withMode(Appearance.Mode.LAYERS)).withSky(SkyPresets.DUSK));
            } else session.setLook(cs, index, target.withMode(modes.get(chosen)));
            // A hidden page of the layer editor must not take the next Esc.
            if (category == Category.SKY) skyLayers.reset();
            return true; // the rest depends on the style
        });
    }

    /** The look of the category, which a group set to Global uses. */
    private Column.Item globalItem(Category category, CategorySettings cs) {
        return Column.of(Ui.H + Ui.GAP, (x, y, iw) -> {
            Form form = form(x, iw);
            ui.swatch(form.lx(), y + 2, 9, 9, chipColor(cs.appearance(), 0xFF808080, null));
            String text = Labels.modeTip(cs.appearance().mode()) + " (the " + Labels.name(category) + " look)";
            ui.text(ui.ellipsize(text, form.cw() - 14), form.lx() + 13, y + 3, Ui.MUTED);
            return false;
        });
    }

    private Column.Item colorItem(CategorySettings cs, int index, Appearance target, boolean recolor, LookBudget budget) {
        return Column.of(budget.colorH, (x, y, iw) -> {
            Form form = form(x, iw);
            form.label(y, Ui.H, "Color", recolor ? "The average color of the result" : null);
            int color = target.color();
            int next = budget.full ? picker.draw("look", form.lx(), y, form.cw(), budget.sqH, color)
                    : picker.compact("look", form.lx(), y, form.cw(), color);
            if (next != color) session.setLook(cs, index, target.withColor(next));
            return false;
        });
    }

    private Column.Item recolorItem(CategorySettings cs, int index, Appearance target, LookBudget budget) {
        return Column.of(budget.recolorHeight(), (x, y, iw) -> {
            int[] v = {target.detail(), target.keepColors(), target.keepLight()};
            form(x, iw).sliders("recolor", "Recolor", Labels.RECOLOR_LABELS, Labels.RECOLOR_TIPS, v, y, budget.oneRow);
            Appearance next = target.withRecolor(v[0], v[1], v[2]);
            if (!next.equals(target)) session.setLook(cs, index, next);
            return false;
        });
    }

    private int shapeHeight(int w) {
        return ui.choiceHeight(Appearance.Alpha.values(), Labels::alpha, w - labelW) + Ui.GAP;
    }

    /** The Shape row, and for players a note under it. */
    private List<Column.Item> shapeItems(Category category, CategorySettings cs, int index, Appearance target, int w, LookBudget budget) {
        List<Column.Item> items = new ArrayList<Column.Item>();
        items.add(Column.of(this::shapeHeight, (x, y, iw) -> {
            Form form = form(x, iw);
            form.label(y, Ui.H, "Shape", null);
            Appearance.Alpha a = ui.choice("alpha", form.lx(), y, form.cw(), Appearance.Alpha.values(), target.alpha(), Labels::alpha,
                    Labels::alphaTip);
            if (a != target.alpha()) session.setLook(cs, index, target.withAlpha(a));
            return false;
        }));
        String shapeNote = "Skins always keep their shape. Shape changes only capes and elytra.";
        // The shape note shows only when the closed rows keep their room, as the Effects heading comes first.
        if (category == Category.PLAYERS && budget.left >= ui.paragraphHeight(shapeNote, w) + Ui.GAP) items.add(note(shapeNote));
        return items;
    }

    /**
     * The Effects heading, and under it when it is open Chroma with its options while it is on (only where the GPU pass
     * moves the colors, Edge only with Outline), then Bevel and Outline for blocks and fluids, and Outline for items.
     */
    private List<Column.Item> effectsSection(Category category, CategorySettings cs, int index, Appearance target) {
        boolean edges = category.hasOutline(), shows = albedo.chromaCanMove(category);
        boolean on = shows && target.chroma().isOn();
        // The first of EFFECT_LABELS that shows: Bevel needs square tiles, so items show only Outline.
        int first = category.hasBevel() ? 0 : 1;
        List<Column.Item> items = new ArrayList<Column.Item>();
        items.add(heading(target, shows, first, edges));
        if (!effects.isOpen()) return items;
        // One item for each group of rows, so that a short window scrolls to each, with one guide line through all of them.
        if (shows) items.add(chromaItem(cs, index, target, on || edges));
        if (on) items.add(chromaOptionsItem(cs, index, target, edges));
        if (edges) items.add(bevelOutlineItem(cs, index, target, first));
        if (on && !albedo.chromaGpuRunning()) {
            items.add(note("Chroma is off, because the GPU cannot move the colors here."));
        }
        return items;
    }

    private Column.Item heading(Appearance target, boolean shows, int first, boolean edges) {
        boolean open = effects.isOpen();
        List<String> names = new ArrayList<String>();
        if (shows) names.add(Labels.CHROMA_LABEL);
        if (edges) names.addAll(Arrays.asList(Labels.EFFECT_LABELS).subList(first, Labels.EFFECT_LABELS.length));
        String tip = (open ? "Hide" : "Show") + " the effects: " + String.join(", ", names);
        String summary = Labels.effects(target, shows, first == 0, edges);
        return Column.of(open ? Ui.H + 2 : SECTION_H, (x, y, w) -> effects.header(x, y, w, "Effects", summary, tip));
    }

    /** The Chroma slider, with a guide line down to the rows under it when more follow. */
    private Column.Item chromaItem(CategorySettings cs, int index, Appearance target, boolean more) {
        Chroma chroma = target.chroma();
        int h = more ? Form.rowsHeight(1) : Form.slidersHeight(1, false);
        return Column.of(h, (x, y, w) -> {
            Form form = form(x, w);
            form.guide(y, more ? h : h - Ui.GAP);
            int strength = form.indented().slider("effects.chroma", y, Labels.CHROMA_LABEL, Labels.CHROMA_TIP, chroma.strength());
            if (strength == chroma.strength()) return false;
            session.setLook(cs, index, target.withEffects(target.bevel(), target.outline(), chroma.withStrength(strength)));
            return (strength > 0) != chroma.isOn(); // the options show or hide under it
        });
    }

    /** The options under Chroma, one indent further right with a guide line of their own. */
    private Column.Item chromaOptionsItem(CategorySettings cs, int index, Appearance target, boolean edges) {
        Chroma chroma = target.chroma();
        int optionRows = Labels.CHROMA_LABELS.length + (edges ? 1 : 0);
        int h = edges ? Form.rowsHeight(optionRows) : Form.slidersHeight(optionRows, false);
        return Column.of(h, (x, y, w) -> {
            Form form = form(x, w);
            form.guide(y, edges ? h : h - Ui.GAP);
            Form rows = form.indented();
            rows.guide(y, Form.rowsHeight(optionRows) - 3);
            int[] o = {chroma.speed(), chroma.saturation(), chroma.brightness()};
            for (int i = 0; i < Labels.CHROMA_LABELS.length; i++) {
                String text = i == 0 && o[i] == 0 ? "Still" : o[i] + "%";
                o[i] = rows.indented().slider(CHROMA_IDS[i], y + Form.rowsHeight(i), Labels.CHROMA_LABELS[i], Labels.CHROMA_TIPS[i], o[i], text);
            }
            boolean edge = edges ? rows.indented().toggle("effects.chromaEdge", y + Form.rowsHeight(Labels.CHROMA_LABELS.length),
                    Labels.EDGE_LABEL, Labels.EDGE_TIP, chroma.edge()) : chroma.edge();
            Chroma next = chroma.withOptions(o[0], o[1], o[2]).withEdge(edge);
            if (!next.equals(chroma)) session.setLook(cs, index, target.withEffects(target.bevel(), target.outline(), next));
            return false;
        });
    }

    /** The Bevel and Outline sliders from the effect {@code first}. */
    private Column.Item bevelOutlineItem(CategorySettings cs, int index, Appearance target, int first) {
        String[] labels = Arrays.copyOfRange(Labels.EFFECT_LABELS, first, Labels.EFFECT_LABELS.length);
        String[] tips = Arrays.copyOfRange(Labels.EFFECT_TIPS, first, Labels.EFFECT_TIPS.length);
        int h = Form.slidersHeight(labels.length, false);
        return Column.of(h, (x, y, w) -> {
            Form form = form(x, w);
            form.guide(y, h - Ui.GAP);
            int[] e = {target.bevel(), target.outline()}, part = Arrays.copyOfRange(e, first, e.length);
            form.indented().sliders(EFFECTS, "Effects", labels, tips, part, y, false);
            System.arraycopy(part, 0, e, first, part.length);
            if (e[0] != target.bevel() || e[1] != target.outline()) session.setLook(cs, index, target.withEffects(e[0], e[1], target.chroma()));
            return false;
        });
    }
}
