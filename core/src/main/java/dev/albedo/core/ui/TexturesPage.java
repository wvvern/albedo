package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Rule;
import dev.albedo.core.texture.TextureKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

import static dev.albedo.core.ui.GroupMatches.shortId;

/** Every texture seen in a category; clicking one opens its group, or starts a group with it. */
final class TexturesPage {
    private static final String LIST = "list";
    private static final int ROW = DropdownRows.ROW;

    private final Ui ui;
    private final Albedo albedo;
    private final DraftSession session;
    private final IntConsumer openGroup;
    private final TextField search = new TextField(64, TextField.ANY);

    /** @param openGroup opens the group of an index of the category */
    TexturesPage(Ui ui, Albedo albedo, DraftSession session, IntConsumer openGroup) {
        this.ui = ui;
        this.albedo = albedo;
        this.session = session;
        this.openGroup = openGroup;
    }

    /** Empties the search and scrolls to the top, for a different page. */
    void reset() {
        search.set("");
        ui.resetScroll(LIST);
    }

    void draw(int x, int y, int w, int h, Category category, CategorySettings cs) {
        Map<String, Integer> catalog = albedo.catalog(category);
        List<String> ids = new ArrayList<String>();
        String q = search.text.trim().toLowerCase(Locale.ROOT);
        for (String id : catalog.keySet()) if (q.isEmpty() || id.contains(q)) ids.add(id);
        int sw = Math.min(150, w);
        if (ui.textField("search", search, x, y, sw, Ui.H, "Search textures", "Type a name, for example leaves or ore")) {
            ui.resetScroll(LIST);
        }
        if (!q.isEmpty()) ui.textFit(ids.size() + " of " + catalog.size(), x + sw + 6, y + 3, w - sw - 6, Ui.MUTED);
        int ly = y + Ui.H + Ui.GAP, lh = h - Ui.H - Ui.GAP;
        if (lh < 20) return;
        ui.round(x, ly, w, lh, Ui.PANEL);
        if (catalog.isEmpty() || ids.isEmpty()) {
            ui.paragraph(catalog.isEmpty() ? "Textures show here after the game loads them."
                    : "No texture name has \"" + q + "\".", x + 6, ly + 6, w - 12, Ui.FAINT);
            return;
        }
        int first = ui.scroll(LIST, x, ly + 2, w, lh - 4, ids.size(), ROW);
        int visible = (lh - 4) / ROW;
        for (int i = first; i < Math.min(ids.size(), first + visible); i++) {
            String id = ids.get(i);
            int ry = ly + 2 + (i - first) * ROW;
            Rule owner = cs.ruleFor(id);
            if (ui.button("row." + id, x + 2, ry, w - 8, ROW, "", Ui.Style.GHOST, true,
                    owner == null ? "Make a new group with " + shortId(id) : "Open group " + owner.displayName())) {
                open(cs, id, owner);
                return;
            }
            int original = catalog.get(id);
            ui.swatch(x + 5, ry + 2, 7, 7, original);
            ui.text(">", x + 14, ry + 1, Ui.FAINT);
            ui.swatch(x + 20, ry + 2, 7, 7, LookSection.chipColor(session.preview().appearanceFor(new TextureKey(category, id)), original, id));
            String tag = owner == null ? "" : owner.displayName();
            int tagW = tag.isEmpty() ? 0 : Math.min(ui.width(tag), w / 3) + 6;
            ui.text(ui.ellipsize(shortId(id), w - 46 - tagW), x + 31, ry + 1, owner == null ? Ui.MUTED : Ui.TEXT);
            if (!tag.isEmpty()) ui.text(ui.ellipsize(tag, tagW - 6), x + w - 8 - tagW, ry + 1, Ui.ACCENT);
        }
    }

    private void open(CategorySettings cs, String id, Rule owner) {
        if (owner != null) {
            openGroup.accept(cs.rules().indexOf(owner));
            return;
        }
        // First place, so that this look wins over a broader group that also matches the texture.
        session.setCategory(cs.withRuleAt(0, cs.newGroup(shortId(id), Collections.singletonList(shortId(id)))));
        openGroup.accept(0);
    }
}
