package dev.plainview.core.ui;

import dev.plainview.core.Category;
import dev.plainview.core.Colors;
import dev.plainview.core.Glob;
import dev.plainview.core.Policy;
import dev.plainview.core.Plainview;
import dev.plainview.core.config.Appearance;
import dev.plainview.core.config.CategorySettings;
import dev.plainview.core.config.Config;
import dev.plainview.core.config.Profile;
import dev.plainview.core.config.Rule;
import dev.plainview.core.config.Toggle;
import dev.plainview.core.texture.TextureKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings screen, version-agnostic. Adapters wrap it in their version's Screen class, forward input and draw
 * it through a {@link Canvas}. It edits a draft {@link Config}; Apply/Done hand the draft to {@link Plainview#apply}.
 *
 * <p>Pages: a category has a global look, ordered groups (each with its own look) and a texture browser; the World
 * page holds the profile's toggles.
 */
public final class SettingsEditor {
    private static final Category[] CATEGORIES = {
            Category.BLOCKS, Category.FLUIDS, Category.BLOCK_ENTITIES, Category.ITEMS, Category.ENTITIES,
            Category.PLAYERS, Category.PARTICLES, Category.BLOCK_BREAKING, Category.SKY, Category.CLOUDS, Category.WEATHER,
    };
    private static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFC8C8C8, 0xFF8C8C8C, 0xFF505050, 0xFF141414, 0xFF000000, 0xFF6D4C41, 0xFFFF7043,
            0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF7CB342, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA,
    };
    /** Colors handed to new groups in turn, so each new group stands out. */
    private static final int[] GROUP_COLORS = {0xFF6D4C41, 0xFF43A047, 0xFF1E88E5, 0xFFE53935, 0xFFFDD835, 0xFF8E24AA};
    /** Texture-name endings that are faces or states of one block, so "grass_block*" can stand for all of them. */
    private static final List<String> FACE_SUFFIXES = Arrays.asList("top", "side", "bottom", "front", "back", "end",
            "overlay", "snow", "inner", "outer", "still", "flow", "base", "open", "on", "off", "lit", "particle");
    private static final int ROW = 11;
    private static final int MAX_SUGGESTIONS = 6;
    /** Width of the "Style" / "Color" / "Shape" labels left of the controls. */
    private static final int LABEL_W = 34;

    private enum Tab { LOOK, GROUPS, TEXTURES }

    private final Plainview sc;
    private final Ui ui = new Ui();
    private Config saved;
    private Config draft;
    private Policy preview;
    private Category category = Category.BLOCKS;
    private Tab tab = Tab.LOOK;
    /** The World page (the profile's toggles) is showing instead of a category. */
    private boolean worldPage;
    /** Index of the group being edited, or -1. */
    private int group = -1;
    /** Shown under the scope switch when a switch was refused. */
    private String notice;
    private int suggestion;
    private final TextField hex = new TextField(9, new TextField.Filter() {
        @Override
        public boolean accepts(char c) { return Character.digit(c, 16) >= 0 || c == '#'; }
    });
    private final TextField search = new TextField(64, TextField.ANY);
    private final TextField add = new TextField(64, new TextField.Filter() {
        @Override
        public boolean accepts(char c) { return Character.isLetterOrDigit(c) || "_-:*?/.".indexOf(c) >= 0; }
    });
    private final TextField groupName = new TextField(32, TextField.ANY);
    private final TextField rename = new TextField(32, new TextField.Filter() {
        @Override
        public boolean accepts(char c) { return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' '; }
    });
    private boolean renaming;
    private boolean closed;
    /** Picker state, kept separately so hue survives at zero saturation/brightness. */
    private float hue, sat, val;
    private int pickerColor = 1; // never a real color, forces the first sync

    public SettingsEditor(Plainview sc) {
        this.sc = sc;
        this.saved = sc.config();
        setDraft(saved);
    }

    // ---------------------------------------------------------------- input from adapters

    public void mouseMoved(double x, double y) { ui.mouseMoved(x, y); }

    public void mousePressed(double x, double y, int button) {
        if (button == 0) ui.mousePressed(x, y);
    }

    public void mouseReleased(double x, double y, int button) {
        if (button == 0) ui.mouseReleased();
    }

    public void mouseScrolled(double amount) { ui.scrolled(amount); }

    public void keyPressed(UiKey key, boolean ctrl) { ui.key(key, ctrl); }

    public void charTyped(char c) { ui.typed(c); }

    /** The adapter closes its screen once this turns true. */
    public boolean isClosed() { return closed; }

    // ---------------------------------------------------------------- frame

    public void render(Canvas canvas, int mouseX, int mouseY) {
        ui.begin(canvas, mouseX, mouseY, new Ui.Clipboard() {
            @Override
            public String get() { return sc.platform().clipboard(); }
        });
        int w = canvas.width(), h = canvas.height();
        int m = w < 440 ? 4 : 10;
        int x0 = m, y0 = m, x1 = w - m, y1 = h - m;
        ui.fill(0, 0, w, h, Ui.SCRIM);
        ui.round(x0, y0, x1 - x0, y1 - y0, Ui.BG);

        header(x0 + 8, y0 + 6, x1 - x0 - 16);
        int bodyTop = y0 + 26, bodyBottom = y1 - 24;
        ui.fill(x0 + 8, bodyTop - 3, x1 - x0 - 16, 1, Ui.BORDER);
        int sideW = Math.min(118, (x1 - x0) / 5 + 24);
        sidebar(x0 + 8, bodyTop + 2, sideW);
        content(x0 + 16 + sideW, bodyTop + 2, x1 - x0 - 24 - sideW, bodyBottom - bodyTop - 2);
        ui.fill(x0 + 8, bodyBottom + 1, x1 - x0 - 16, 1, Ui.BORDER);
        footer(x0 + 8, bodyBottom + 5, x1 - x0 - 16);

        if (ui.takeKey(UiKey.ESCAPE)) {
            if (group >= 0) openGroup(-1);
            else done();
        }
        ui.end();
    }

    // ---------------------------------------------------------------- header: title, on/off, profiles

    private void header(int x, int y, int w) {
        ui.text(Plainview.NAME, x, y + 3, Ui.TEXT);
        int tx = x + ui.width(Plainview.NAME) + 8;
        boolean on = ui.toggle("enabled", tx, y + 2, draft.enabled(), "Turn the whole mod on or off");
        if (on != draft.enabled()) setDraft(draft.withEnabled(on));
        ui.text(on ? "On" : "Off", tx + 22, y + 3, on ? Ui.TEXT : Ui.MUTED);

        int bw = 40, gap = 3, right = x + w;
        boolean single = draft.profiles().size() <= 1;
        if (ui.button("delete", right - bw, y, bw, 14, "Delete", Ui.Style.DANGER, !single, "Delete this profile")) {
            setDraft(draft.withoutProfile(draft.activeProfileName()));
        }
        if (ui.button("rename", right - 2 * bw - gap, y, bw, 14, "Rename", Ui.Style.NORMAL, true, "Rename this profile")) {
            startRename();
        }
        if (ui.button("new", right - 3 * bw - 2 * gap, y, bw, 14, "Copy", Ui.Style.NORMAL, true,
                "New profile, starting as a copy of this one")) {
            String name = draft.freeProfileName(draft.activeProfileName() + "-copy");
            setDraft(draft.withProfile(draft.activeProfile().renamed(name)).withActiveProfile(name));
            startRename();
        }

        int px = tx + 44, pw = right - 3 * bw - 2 * gap - 8 - px;
        if (pw < 60) return;
        if (renaming) {
            ui.textField("rename", rename, px + 14, y, pw - 28, 14, "profile name", "Enter to save, Esc to cancel");
            if (!ui.isFocused("rename")) finishRename();
            return;
        }
        if (ui.button("prev", px, y, 13, 14, "<", Ui.Style.GHOST, !single, "Previous profile")) {
            setDraft(draft.withActiveProfile(draft.profileNameAt(-1)));
        }
        if (ui.button("next", px + pw - 13, y, 13, 14, ">", Ui.Style.GHOST, !single, "Next profile")) {
            setDraft(draft.withActiveProfile(draft.profileNameAt(1)));
        }
        ui.round(px + 14, y, pw - 28, 14, Ui.CARD);
        ui.textCentered(ui.ellipsize("Profile: " + draft.activeProfileName(), pw - 32), px + pw / 2, y + 3, Ui.TEXT);
    }

    private void startRename() {
        renaming = true;
        rename.set(draft.activeProfileName());
        ui.focus("rename");
    }

    private void finishRename() {
        renaming = false;
        String name = rename.text.trim().replace(' ', '-').toLowerCase(Locale.ROOT);
        if (!name.isEmpty()) setDraft(draft.withRenamedProfile(draft.activeProfileName(), name));
    }

    // ---------------------------------------------------------------- sidebar: categories, then the World page

    private void sidebar(int x, int y, int w) {
        int ry = y;
        for (Category c : CATEGORIES) {
            Appearance a = profile().get(c).appearance();
            if (sidebarRow("cat." + c.key(), x, ry, w, name(c), description(c), !worldPage && c == category, !a.isOriginal())) {
                category = c;
                worldPage = false;
                tab = Tab.LOOK;
                openGroup(-1);
                search.set("");
                ui.resetScroll("list");
            }
            if (a.isOriginal()) ui.outline(x + 6, ry + 2, 8, 8, Ui.FAINT);
            else ui.swatch(x + 6, ry + 2, 8, 8, chipColor(a, 0xFF808080));
            ry += 13;
        }
        ry += 5;
        ui.fill(x + 4, ry - 3, w - 8, 1, Ui.BORDER);
        if (sidebarRow("world", x, ry, w, "World", "Fullbright, shading, foliage, shadows", worldPage, true)) {
            worldPage = true;
            openGroup(-1);
        }
        // a small sun as the World icon
        ui.fill(x + 9, ry + 2, 2, 8, Ui.MUTED);
        ui.fill(x + 6, ry + 5, 8, 2, Ui.MUTED);
        ui.fill(x + 8, ry + 4, 4, 4, Ui.TEXT);
    }

    /** A selectable sidebar entry; the icon is drawn by the caller at x + 6. Returns true when clicked. */
    private boolean sidebarRow(String id, int x, int y, int w, String label, String tip, boolean active, boolean bright) {
        boolean clicked = false;
        if (active) {
            ui.round(x, y, w, 12, Ui.CARD);
            ui.fill(x, y + 2, 2, 8, Ui.ACCENT);
        } else {
            clicked = ui.button(id, x, y, w, 12, "", Ui.Style.GHOST, true, tip);
        }
        ui.text(ui.ellipsize(label, w - 20), x + 18, y + 2, active || bright ? Ui.TEXT : Ui.MUTED);
        return clicked;
    }

    // ---------------------------------------------------------------- content

    private void content(int x, int y, int w, int h) {
        if (worldPage) {
            worldPage(x, y, w);
            return;
        }
        CategorySettings cs = profile().get(category);
        if (group >= 0 && group < cs.rules().size()) {
            groupPage(x, y, w, y + h, cs, group);
            return;
        }
        group = -1;
        ui.text(name(category), x, y, Ui.TEXT);
        int cy = y + 13;
        if (hasGroups(category)) {
            Tab[] tabs = Tab.values();
            String[] labels = {"Look", "Groups " + cs.rules().size(), "Textures " + sc.catalog(category).size()};
            int picked = ui.segmented("tab", x, cy, Math.min(w, 240), 14, labels, tab.ordinal(), new String[]{
                    "The look of every " + name(category).toLowerCase(Locale.ROOT) + " texture without a group",
                    "Sets of blocks with their own look, or kept vanilla",
                    "Every texture the game has loaded, to put into groups"});
            if (picked != tab.ordinal()) {
                tab = tabs[picked];
                notice = null;
                return;
            }
            cy += 20;
        } else {
            ui.text(ui.ellipsize(description(category), w - ui.width(name(category)) - 8), x + ui.width(name(category)) + 8, y, Ui.MUTED);
            tab = Tab.LOOK;
        }
        if (tab == Tab.LOOK) lookTab(x, cy, w, cs);
        else if (tab == Tab.GROUPS) groupsTab(x, cy, w, y + h - cy, cs);
        else texturesTab(x, cy, w, y + h - cy, cs);
    }

    private void lookTab(int x, int y, int w, CategorySettings cs) {
        int cy = lookEditor(x, y, w, cs, -1);
        if (cs.onlyGroups()) {
            ui.text(ui.ellipsize("Only groups change. This look applies to groups set to \"Global\".", w), x, cy, Ui.FAINT);
            cy += 13;
        }
        if (category == Category.BLOCKS || category == Category.FLUIDS) {
            boolean tint = ui.toggleRow("tint", x - 4, cy, w + 4, "Remove biome tint", cs.removeTint(),
                    category == Category.BLOCKS ? "Grass and leaves stop being colored by biome" : "Water stops being colored by biome");
            if (tint != cs.removeTint()) setCategory(cs.withRemoveTint(tint));
        }
        if (category == Category.SKY) ui.text(ui.ellipsize("Also sets fog, so the horizon blends into the flat sky.", w), x, cy, Ui.FAINT);
    }

    /**
     * Style, color and shape controls for the global look ({@code index} -1) or a group. Returns the next y.
     * A group's first style is "Global" when only groups change, otherwise "Vanilla" (which keeps its blocks as they are).
     */
    private int lookEditor(int x, int y, int w, CategorySettings cs, int index) {
        Rule rule = index < 0 ? null : cs.rules().get(index);
        boolean global = rule != null && rule.usesGlobalLook();
        Appearance target = rule == null ? cs.appearance() : rule.appearance();
        Appearance.Mode[] modes = modes(category);
        String[] labels = new String[modes.length], tips = new String[modes.length];
        int selected = 0;
        for (int i = 0; i < modes.length; i++) {
            labels[i] = modeLabel(modes[i]);
            tips[i] = modeTip(modes[i]);
            if (!global && modes[i] == target.mode()) selected = i;
        }
        if (rule != null && cs.onlyGroups()) {
            labels[0] = "Global";
            tips[0] = "Use the " + name(category) + " look";
        } else if (rule != null) {
            tips[0] = "Keep these blocks vanilla while everything else changes";
        }
        ui.text("Style", x, y + 3, Ui.MUTED);
        int lx = x + LABEL_W, cw = w - LABEL_W;
        int picked = ui.segmented("mode", lx, y, cw, 14, labels, selected, tips);
        if (picked != selected) {
            if (rule != null && cs.onlyGroups() && picked == 0) setRule(cs, index, rule.withGlobalLook());
            else setLook(cs, index, target.withMode(modes[picked]));
            return y + 20; // the rest of the layout depends on the style; draw it next frame
        }
        int cy = y + 20;
        if (global) {
            ui.swatch(lx, cy + 2, 9, 9, chipColor(cs.appearance(), 0xFF808080));
            ui.text(ui.ellipsize(modeTip(cs.appearance().mode()) + " (the " + name(category) + " look)", cw - 14), lx + 13, cy + 3, Ui.MUTED);
            return cy + 16;
        }
        if (target.mode() == Appearance.Mode.TEXTURE) cy = texturePicker(x, cy, w, cs, index, target);
        if (target.mode() == Appearance.Mode.SOLID || target.mode() == Appearance.Mode.TEXTURE) {
            ui.text(target.mode() == Appearance.Mode.TEXTURE ? "Fallback" : "Color", x, cy + 3, Ui.MUTED);
            cy = colorPicker(lx, cy, cw, cs, index, target);
        }
        boolean usesShape = target.mode() != Appearance.Mode.ORIGINAL && target.mode() != Appearance.Mode.HIDDEN
                && category != Category.SKY && category != Category.CLOUDS;
        if (usesShape) {
            ui.text("Shape", x, cy + 3, Ui.MUTED);
            int a = ui.segmented("alpha", lx, cy, cw, 14, new String[]{"Keep", "Cut-out", "Solid"}, target.alpha().ordinal(),
                    new String[]{"Keep see-through parts as they are (glass, leaves)",
                            "Half-transparent parts become either solid or invisible",
                            "Fill every hole: leaves and glass become solid cubes"});
            if (a != target.alpha().ordinal()) setLook(cs, index, target.withAlpha(Appearance.Alpha.values()[a]));
            cy += 20;
        }
        return cy;
    }

    // ---------------------------------------------------------------- groups

    private void groupsTab(int x, int y, int w, int h, CategorySettings cs) {
        ui.text("Change", x, y + 3, Ui.MUTED);
        int scope = ui.segmented("scope", x + LABEL_W, y, w - LABEL_W, 14, new String[]{"Everything", "Only groups"},
                cs.onlyGroups() ? 1 : 0, new String[]{
                        "The " + name(category) + " look applies to all; groups override it or keep blocks vanilla",
                        "Only blocks in groups change; everything else stays vanilla"});
        if (scope != (cs.onlyGroups() ? 1 : 0)) {
            Rule blocker = cs.blocksScopeSwitch();
            if (blocker == null) {
                setCategory(cs.withOnlyGroups(scope == 1));
                notice = null;
            } else {
                notice = "Group \"" + blocker.displayName() + "\" is set to " + (cs.onlyGroups() ? "Global" : "Vanilla")
                        + ", which has no meaning in the other mode. Change it first.";
            }
            return;
        }
        int cy = y + 18;
        if (notice != null) cy += ui.paragraph(notice, x, cy, w, Ui.DANGER) + 3;
        if (ui.button("group.new", x, cy, 70, 14, "+ New group", Ui.Style.PRIMARY, true, "A set of blocks with its own look")) {
            List<Rule> rules = new ArrayList<Rule>(cs.rules());
            int color = GROUP_COLORS[rules.size() % GROUP_COLORS.length];
            rules.add(new Rule(freeGroupName(cs), Collections.<String>emptyList(), Appearance.solid(color), false));
            setCategory(cs.withRules(rules));
            openGroup(rules.size() - 1);
            ui.focus("add");
            return;
        }
        ui.text(ui.ellipsize("Higher groups win when a block is in two.", w - 78), x + 76, cy + 3, Ui.FAINT);
        cy += 18;
        List<Rule> rules = cs.rules();
        if (rules.isEmpty()) {
            ui.text(ui.ellipsize(cs.onlyGroups() ? "No groups yet, so nothing changes." : "No groups yet: every texture uses the look.", w), x, cy + 2, Ui.FAINT);
            return;
        }
        int rowH = 16, lh = y + h - cy;
        int first = ui.scroll("groups", x, cy, w, lh, rules.size(), rowH);
        for (int i = first; i < Math.min(rules.size(), first + Math.max(1, lh / rowH)); i++) {
            Rule r = rules.get(i);
            int ry = cy + (i - first) * rowH;
            ui.round(x, ry, w - 6, rowH - 2, Ui.PANEL);
            if (ui.button("group." + i, x, ry, w - 58, rowH - 2, "", Ui.Style.GHOST, true, "Edit this group")) {
                openGroup(i);
                return;
            }
            Appearance a = r.usesGlobalLook() ? cs.appearance() : r.appearance();
            if (a.isOriginal()) ui.outline(x + 4, ry + 3, 8, 8, Ui.FAINT);
            else ui.swatch(x + 4, ry + 3, 8, 8, chipColor(a, 0xFF808080));
            String look = r.usesGlobalLook() ? "global look" : a.isOriginal() ? "kept vanilla" : modeLabel(a.mode()).toLowerCase(Locale.ROOT);
            int nameW = Math.min(ui.width(r.displayName()), (w - 70) / 2);
            ui.text(ui.ellipsize(r.displayName(), nameW), x + 16, ry + 3, Ui.TEXT);
            String members = r.patterns().isEmpty() ? "empty" : join(r.patterns());
            ui.text(ui.ellipsize(look + "  -  " + members, w - 80 - nameW), x + 22 + nameW, ry + 3, Ui.MUTED);
            int bx = x + w - 56;
            if (ui.button("group.up." + i, bx, ry + 1, 15, 12, "^", Ui.Style.GHOST, i > 0, "Higher priority")) move(cs, i, -1);
            if (ui.button("group.down." + i, bx + 16, ry + 1, 15, 12, "v", Ui.Style.GHOST, i < rules.size() - 1, "Lower priority")) move(cs, i, 1);
            if (ui.button("group.del." + i, bx + 34, ry + 1, 15, 12, "x", Ui.Style.DANGER, true, "Delete this group")) {
                List<Rule> next = new ArrayList<Rule>(rules);
                next.remove(i);
                setCategory(cs.withRules(next));
                return;
            }
        }
    }

    private void groupPage(int x, int y, int w, int bottom, CategorySettings cs, int index) {
        Rule rule = cs.rules().get(index);
        if (ui.button("back", x, y - 2, 12, 12, "<", Ui.Style.GHOST, true, "Back to the groups (Esc)")) {
            openGroup(-1);
            return;
        }
        ui.text(name(category) + " /", x + 15, y, Ui.MUTED);
        int nx = x + 19 + ui.width(name(category) + " /");
        if (!ui.isFocused("group.name")) groupName.set(rule.displayName());
        if (ui.textField("group.name", groupName, nx, y - 3, Math.min(120, w - (nx - x) - 50), 14, "group name", "Rename this group")
                && !groupName.text.trim().isEmpty()) {
            setRule(cs, index, rule.withName(groupName.text.trim()));
            return;
        }
        if (ui.button("group.delete", x + w - 44, y - 3, 44, 14, "Delete", Ui.Style.DANGER, true, "Delete this group")) {
            List<Rule> next = new ArrayList<Rule>(cs.rules());
            next.remove(index);
            setCategory(cs.withRules(next));
            openGroup(-1);
            return;
        }
        int cy = lookEditor(x, y + 15, w, cs, index);
        blocksEditor(x, cy + 2, w, bottom, cs, index);
    }

    /** The group's blocks as removable chips, and an input that suggests matching textures while typing. */
    private void blocksEditor(int x, int y, int w, int bottom, CategorySettings cs, int index) {
        Rule rule = cs.rules().get(index);
        Map<String, Integer> catalog = sc.catalog(category);
        ui.text("Blocks", x, y + 3, Ui.MUTED);
        int fx = x + LABEL_W, fw = w - LABEL_W;
        // Keys the list reacts to are taken before the field would (Enter would unfocus it); typing goes to the field.
        boolean down = false, up = false, complete = false, enter = false;
        if (ui.isFocused("add")) {
            down = ui.takeKey(UiKey.DOWN);
            up = ui.takeKey(UiKey.UP);
            complete = ui.takeKey(UiKey.TAB);
            enter = ui.takeKey(UiKey.ENTER);
        }
        if (ui.textField("add", add, fx, y, fw, 14, "Add a block: type a name, e.g. dirt",
                "Enter adds the highlighted match, Tab completes, * matches anything")) {
            suggestion = 0;
        }
        List<Suggestion> list = ui.isFocused("add") ? suggestions(add.text, catalog, cs, index) : Collections.<Suggestion>emptyList();
        if (down) suggestion++;
        if (up) suggestion--;
        suggestion = Math.max(0, Math.min(suggestion, list.size() - 1));
        if (complete && !list.isEmpty()) add.set(list.get(suggestion).pattern);
        if (enter && !add.text.trim().isEmpty()) {
            addPattern(cs, index, list.isEmpty() ? cleanPattern(add.text) : list.get(suggestion).pattern);
            return;
        }
        int listY = y + 16, rows = Math.min(list.size(), Math.max(0, (bottom - listY) / ROW));
        if (rows > 0) ui.reserveOverlay(fx, listY, fw, rows * ROW + 2);

        // chips
        int cx = fx, cyy = listY + 2;
        for (int i = 0; i < rule.patterns().size(); i++) {
            String p = rule.patterns().get(i);
            int[] stats = chipStats(cs, rule, p, catalog);
            int matched = stats[0], effective = stats[1];
            Integer color = matched > 0 ? stats[2] : null;
            boolean dim = matched == 0 || effective == 0;
            String tip = matched == 0 ? "No loaded texture is called " + p
                    : effective == 0 ? "A higher group already takes all " + matched + " of these"
                    : effective + " texture" + (effective == 1 ? "" : "s");
            int cw = 4 + (color != null ? 10 : 0) + ui.width(p) + 12;
            if (cx + cw > fx + fw && cx > fx) {
                cx = fx;
                cyy += 14;
            }
            if (cyy + 12 > bottom) break;
            boolean[] removed = {false};
            cx += ui.chip("chip." + i + "." + p, cx, cyy, color, p, dim, tip, removed) + 3;
            if (removed[0]) {
                List<String> next = new ArrayList<String>(rule.patterns());
                next.remove(i);
                setRule(cs, index, rule.withPatterns(next));
                return;
            }
        }
        if (rule.patterns().isEmpty() && rows == 0) {
            ui.text(ui.ellipsize("Empty: type a block name above.", fw), fx, cyy + 2, Ui.FAINT);
        }

        // suggestions, drawn over the chips
        if (rows > 0) {
            ui.beginOverlay();
            ui.round(fx, listY, fw, rows * ROW + 2, Ui.BORDER);
            ui.round(fx + 1, listY + 1, fw - 2, rows * ROW, Ui.PANEL);
            for (int i = 0; i < rows; i++) {
                Suggestion s = list.get(i);
                int ry = listY + 1 + i * ROW;
                if (i == suggestion) ui.fill(fx + 1, ry, fw - 2, ROW, Ui.CARD_HOVER);
                if (ui.button("sugg." + i, fx + 1, ry, fw - 2, ROW, "", Ui.Style.GHOST, true, null)) {
                    addPattern(cs, index, s.pattern);
                    ui.focus("add");
                    return;
                }
                ui.swatch(fx + 4, ry + 2, 7, 7, s.color);
                String right = (s.count > 1 ? s.count + " textures" : "") + (s.owner != null ? "  in " + s.owner : "");
                int rw = right.isEmpty() ? 0 : ui.width(right) + 6;
                ui.text(ui.ellipsize(s.pattern, fw - 20 - rw), fx + 15, ry + 1, Ui.TEXT);
                if (rw > 0) ui.text(right, fx + fw - rw - 2, ry + 1, s.owner != null ? Ui.DANGER : Ui.FAINT);
            }
            ui.endOverlay();
        }
    }

    // Scanning ~1000 textures with globs is too slow to do every frame, so results are kept until the groups change.
    private CategorySettings memoFor;
    private int memoCatalogSize;
    private final Map<String, int[]> chipMemo = new LinkedHashMap<String, int[]>();
    private final Map<String, List<Suggestion>> suggestionMemo = new LinkedHashMap<String, List<Suggestion>>();

    private void checkMemo(CategorySettings cs, Map<String, Integer> catalog) {
        if (memoFor != cs || memoCatalogSize != catalog.size()) {
            memoFor = cs;
            memoCatalogSize = catalog.size();
            chipMemo.clear();
            suggestionMemo.clear();
        }
    }

    /** {textures matched, textures this group actually gets (no higher group took them), first match's color}. */
    private int[] chipStats(CategorySettings cs, Rule rule, String pattern, Map<String, Integer> catalog) {
        checkMemo(cs, catalog);
        String key = cs.rules().indexOf(rule) + "|" + pattern;
        int[] stats = chipMemo.get(key);
        if (stats != null) return stats;
        Glob g = Glob.compile(pattern);
        stats = new int[3];
        for (Map.Entry<String, Integer> e : catalog.entrySet()) {
            if (!g.matches(e.getKey())) continue;
            if (stats[0]++ == 0) stats[2] = e.getValue();
            if (cs.ruleFor(e.getKey()) == rule) stats[1]++;
        }
        chipMemo.put(key, stats);
        return stats;
    }

    /** Adds a pattern to group {@code index}; the same pattern in another group moves here. */
    private void addPattern(CategorySettings cs, int index, String pattern) {
        add.set("");
        suggestion = 0;
        if (pattern.isEmpty()) return;
        List<Rule> rules = new ArrayList<Rule>();
        for (int i = 0; i < cs.rules().size(); i++) {
            Rule r = cs.rules().get(i);
            List<String> p = new ArrayList<String>(r.patterns());
            if (i == index) {
                if (!p.contains(pattern)) p.add(pattern);
            } else {
                p.remove(pattern);
            }
            rules.add(r.withPatterns(p));
        }
        setCategory(cs.withRules(rules));
        ui.focus("add");
    }

    private static final class Suggestion {
        final String pattern;
        final int count;
        final Integer color;
        final String owner;

        Suggestion(String pattern, int count, Integer color, String owner) {
            this.pattern = pattern;
            this.count = count;
            this.color = color;
            this.owner = owner;
        }
    }

    /**
     * Matches for what's typed: whole blocks first ("grass_block*" covers its top, sides and overlay), then single
     * textures; names starting with the text before names containing it. A typed pattern with * is offered as is.
     */
    private List<Suggestion> suggestions(String typed, Map<String, Integer> catalog, CategorySettings cs, int index) {
        String q = typed.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return Collections.emptyList();
        checkMemo(cs, catalog);
        String key = index + "|" + q;
        List<Suggestion> cached = suggestionMemo.get(key);
        if (cached == null) {
            cached = findSuggestions(q, catalog, cs, index);
            suggestionMemo.put(key, cached);
        }
        return cached;
    }

    private List<Suggestion> findSuggestions(String q, Map<String, Integer> catalog, CategorySettings cs, int index) {
        Rule self = cs.rules().get(index);
        List<Suggestion> out = new ArrayList<Suggestion>();
        if (q.indexOf('*') >= 0 || q.indexOf('?') >= 0) {
            out.add(suggestionFor(cleanPattern(q), catalog, cs, self));
            return out;
        }
        Map<String, List<String>> stems = new LinkedHashMap<String, List<String>>();
        List<String> singles = new ArrayList<String>();
        for (String id : catalog.keySet()) {
            String s = shortId(id);
            if (!s.contains(q)) continue;
            singles.add(s);
            String stem = stem(s);
            if (!stems.containsKey(stem)) stems.put(stem, new ArrayList<String>());
            stems.get(stem).add(s);
        }
        List<String> candidates = new ArrayList<String>();
        for (Map.Entry<String, List<String>> e : stems.entrySet()) {
            if (e.getValue().size() > 1 && wholeBlock(e.getKey(), catalog)) candidates.add(e.getKey() + "*");
        }
        candidates.addAll(singles);
        List<String> starts = new ArrayList<String>(), contains = new ArrayList<String>();
        for (String c : candidates) {
            if (self.patterns().contains(c)) continue;
            (c.startsWith(q) ? starts : contains).add(c);
        }
        for (String c : starts) if (out.size() < MAX_SUGGESTIONS) out.add(suggestionFor(c, catalog, cs, self));
        for (String c : contains) if (out.size() < MAX_SUGGESTIONS) out.add(suggestionFor(c, catalog, cs, self));
        return out;
    }

    private Suggestion suggestionFor(String pattern, Map<String, Integer> catalog, CategorySettings cs, Rule self) {
        Glob g = Glob.compile(pattern);
        int count = 0;
        Integer color = null;
        String owner = null;
        for (Map.Entry<String, Integer> e : catalog.entrySet()) {
            if (!g.matches(e.getKey())) continue;
            count++;
            if (color == null) color = e.getValue();
            Rule r = cs.ruleFor(e.getKey());
            if (owner == null && r != null && r != self && r.patterns().contains(pattern)) owner = r.displayName();
        }
        return new Suggestion(pattern, count, color, owner);
    }

    /** "grass_block_top" -> "grass_block"; names without a face suffix are their own stem. */
    private static String stem(String id) {
        int u = id.lastIndexOf('_');
        return u > 0 && FACE_SUFFIXES.contains(id.substring(u + 1)) ? id.substring(0, u) : id;
    }

    /** True when "stem*" matches only that block's own textures (not e.g. "stone*" matching stone_bricks). */
    private static boolean wholeBlock(String stem, Map<String, Integer> catalog) {
        Glob g = Glob.compile(stem + "*");
        for (String id : catalog.keySet()) {
            String s = shortId(id);
            if (g.matches(id) && !stem(s).equals(stem)) return false;
        }
        return true;
    }

    private static String cleanPattern(String text) {
        return shortId(text.trim().toLowerCase(Locale.ROOT));
    }

    private void move(CategorySettings cs, int i, int by) {
        List<Rule> rules = new ArrayList<Rule>(cs.rules());
        Collections.swap(rules, i, i + by);
        setCategory(cs.withRules(rules));
    }

    private String freeGroupName(CategorySettings cs) {
        for (int n = cs.rules().size() + 1; ; n++) {
            boolean taken = false;
            for (Rule r : cs.rules()) if (r.displayName().equals("Group " + n)) taken = true;
            if (!taken) return "Group " + n;
        }
    }

    private void openGroup(int index) {
        group = index;
        add.set("");
        suggestion = 0;
        ui.clearFocus();
    }

    // ---------------------------------------------------------------- textures

    /** Every texture seen in this category; clicking one opens its group, or starts a group with it. */
    private void texturesTab(int x, int y, int w, int h, CategorySettings cs) {
        Map<String, Integer> catalog = sc.catalog(category);
        List<String> ids = new ArrayList<String>();
        String q = search.text.trim().toLowerCase(Locale.ROOT);
        for (String id : catalog.keySet()) if (q.isEmpty() || id.contains(q)) ids.add(id);
        if (ui.textField("search", search, x, y, Math.min(140, w), 14, "Search...", "Filter by name, e.g. leaves or ore")) {
            ui.resetScroll("list");
        }
        ui.text(ui.ellipsize("Click a texture to put it in a group", w - 150), x + Math.min(140, w) + 8, y + 3, Ui.FAINT);
        int ly = y + 18, lh = h - 18;
        if (lh < 20) return;
        ui.round(x, ly, w, lh, Ui.PANEL);
        if (catalog.isEmpty()) {
            ui.text(ui.ellipsize("Textures show up here once the game has loaded them.", w - 12), x + 6, ly + 6, Ui.FAINT);
            return;
        }
        int first = ui.scroll("list", x, ly + 2, w, lh - 4, ids.size(), ROW);
        int visible = (lh - 4) / ROW;
        for (int i = first; i < Math.min(ids.size(), first + visible); i++) {
            String id = ids.get(i);
            int ry = ly + 2 + (i - first) * ROW;
            Rule owner = cs.ruleFor(id);
            if (ui.button("row." + id, x + 2, ry, w - 8, ROW, "", Ui.Style.GHOST, true,
                    owner == null ? "Start a group with " + shortId(id) : "Open group \"" + owner.displayName() + "\"")) {
                openTextureGroup(cs, id, owner);
                return;
            }
            int original = catalog.get(id);
            ui.swatch(x + 5, ry + 2, 7, 7, original);
            ui.text(">", x + 14, ry + 1, Ui.FAINT);
            ui.swatch(x + 20, ry + 2, 7, 7, chipColor(preview.appearanceFor(new TextureKey(category, id)), original));
            String tag = owner == null ? "" : owner.displayName();
            int tagW = tag.isEmpty() ? 0 : Math.min(ui.width(tag), w / 3) + 6;
            ui.text(ui.ellipsize(shortId(id), w - 46 - tagW), x + 31, ry + 1, Ui.MUTED);
            if (!tag.isEmpty()) ui.text(ui.ellipsize(tag, tagW - 6), x + w - 8 - tagW, ry + 1, Ui.ACCENT);
        }
    }

    private void openTextureGroup(CategorySettings cs, String id, Rule owner) {
        if (owner != null) {
            openGroup(cs.rules().indexOf(owner));
        } else {
            List<Rule> rules = new ArrayList<Rule>(cs.rules());
            // Under "only groups" a new group takes the global look, so picking a texture just switches it on.
            rules.add(0, new Rule(shortId(id), Collections.singletonList(shortId(id)),
                    Appearance.solid(GROUP_COLORS[rules.size() % GROUP_COLORS.length]), cs.onlyGroups()));
            setCategory(cs.withRules(rules));
            openGroup(0);
        }
    }

    // ---------------------------------------------------------------- world page

    private void worldPage(int x, int y, int w) {
        ui.text("World", x, y, Ui.TEXT);
        ui.text(ui.ellipsize("Applies to everything, whatever the categories say", w - 40), x + 40, y, Ui.MUTED);
        int cy = y + 16;
        for (Toggle t : Toggle.values()) {
            ui.round(x, cy, w, 26, Ui.PANEL);
            boolean on = profile().is(t);
            if (ui.toggleRow(t.key, x + 2, cy + 2, w - 4, label(t), on, explain(t)) != on) setProfile(profile().with(t, !on));
            ui.text(ui.ellipsize(explain(t), w - 12), x + 6, cy + 15, Ui.MUTED);
            cy += 30;
        }
    }

    private static String label(Toggle t) {
        switch (t) {
            case FULLBRIGHT: return "Fullbright";
            case SHADING: return "Shading";
            case FOLIAGE: return "Foliage";
            default: return "Entity shadows";
        }
    }

    private static String explain(Toggle t) {
        switch (t) {
            case FULLBRIGHT: return "Everything is lit as if at noon: no dark caves, no night.";
            case SHADING: return "Smooth lighting and darker block sides. Off: every surface is flat.";
            case FOLIAGE: return "Grass, flowers, ferns, vines and other small plants. Off: hidden.";
            default: return "The round shadows under mobs and players.";
        }
    }

    // ---------------------------------------------------------------- color and image pickers

    private int texturePicker(int x, int y, int w, CategorySettings cs, int index, Appearance target) {
        List<String> names = sc.userTextureNames();
        ui.text("Image", x, y + 3, Ui.MUTED);
        int lx = x + LABEL_W, lw = w - LABEL_W;
        if (names.isEmpty()) {
            ui.text(ui.ellipsize("Put PNG files in config/plainview/textures", lw), lx, y + 3, Ui.FAINT);
            return y + 18;
        }
        int i = Math.max(0, names.indexOf(target.texture()));
        if (ui.button("tex.prev", lx, y, 13, 14, "<", Ui.Style.GHOST, names.size() > 1, null)) i = (i + names.size() - 1) % names.size();
        if (ui.button("tex.next", lx + lw - 13, y, 13, 14, ">", Ui.Style.GHOST, names.size() > 1, null)) i = (i + 1) % names.size();
        ui.round(lx + 14, y, lw - 28, 14, Ui.CARD);
        ui.textCentered(ui.ellipsize(names.get(i), lw - 32), lx + lw / 2, y + 3, Ui.TEXT);
        if (!names.get(i).equals(target.texture())) setLook(cs, index, target.withTexture(names.get(i)));
        return y + 20;
    }

    /** Saturation/brightness square, hue and opacity bars, hex input and a palette. Returns the next y. */
    private int colorPicker(int x, int y, int w, CategorySettings cs, int index, Appearance target) {
        int color = target.color();
        if (color != pickerColor) syncPicker(color);
        int alpha = color >>> 24;
        int sqW = Math.min(84, w / 3), sqH = 44;

        // saturation (x) / brightness (y) square, drawn in 3px cells
        for (int yy = 0; yy < sqH; yy += 3) {
            for (int xx = 0; xx < sqW; xx += 3) {
                int cell = Ui.hsv(hue, xx / (float) (sqW - 1), 1 - yy / (float) (sqH - 1));
                ui.fill(x + xx, y + yy, Math.min(3, sqW - xx), Math.min(3, sqH - yy), cell);
            }
        }
        int kx = x + Math.round(sat * (sqW - 1)), ky = y + Math.round((1 - val) * (sqH - 1));
        ui.outline(kx - 2, ky - 2, 5, 5, val > 0.5f ? 0xFF000000 : 0xFFFFFFFF);
        float[] sv = ui.dragArea("sv", x, y, sqW, sqH);
        if (sv != null) {
            sat = sv[0];
            val = 1 - sv[1];
        }

        // hue bar
        int hx = x + sqW + 4;
        for (int yy = 0; yy < sqH; yy += 2) ui.fill(hx, y + yy, 8, Math.min(2, sqH - yy), Ui.hsv(yy / (float) sqH, 1, 1));
        ui.fill(hx - 1, y + Math.round(hue * (sqH - 1)), 10, 1, 0xFFFFFFFF);
        float[] hv = ui.dragArea("hue", hx, y, 8, sqH);
        if (hv != null) hue = Math.min(hv[1], 0.999f);

        // opacity bar
        int ax = hx + 12;
        ui.checker(ax, y, 8, sqH);
        int rgb = Ui.hsv(hue, sat, val) & 0xFFFFFF;
        for (int yy = 0; yy < sqH; yy += 2) ui.fill(ax, y + yy, 8, Math.min(2, sqH - yy), ((255 - yy * 255 / sqH) << 24) | rgb);
        ui.fill(ax - 1, y + Math.round((1 - alpha / 255f) * (sqH - 1)), 10, 1, 0xFFFFFFFF);
        float[] av = ui.dragArea("alpha.bar", ax, y, 8, sqH);
        if (av != null) alpha = Math.round((1 - av[1]) * 255);

        int next = (alpha << 24) | (Ui.hsv(hue, sat, val) & 0xFFFFFF);
        if (sv != null || hv != null || av != null) {
            if (av != null && sv == null && hv == null) next = (alpha << 24) | (color & 0xFFFFFF);
            pickerColor = next;
            setLook(cs, index, target.withColor(next));
        }

        // preview, hex and palette
        int px = ax + 14, pw = Math.max(40, x + w - px);
        ui.swatch(px, y, 18, 18, next);
        if (!ui.isFocused("hex")) hex.set(Colors.format(color));
        if (ui.textField("hex", hex, px + 22, y + 2, Math.min(62, pw - 22), 14, "#RRGGBB", "Type a color: #RGB, #RRGGBB or #AARRGGBB")) {
            try {
                int typed = Colors.parse(hex.text);
                syncPicker(typed);
                setLook(cs, index, target.withColor(typed));
            } catch (IllegalArgumentException ignored) {
                // keep typing
            }
        }
        int sw = 9, perRow = Math.max(1, Math.min(8, (pw + 2) / (sw + 2)));
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = px + (i % perRow) * (sw + 2), sy = y + 22 + (i / perRow) * (sw + 2);
            if (sy + sw > y + sqH + 12) break;
            boolean clicked = ui.button("pal." + i, sx - 1, sy - 1, sw + 2, sw + 2, "", Ui.Style.GHOST, true, Colors.format(PALETTE[i]));
            ui.swatch(sx, sy, sw, sw, PALETTE[i]);
            if (clicked) {
                int c = (PALETTE[i] & 0xFFFFFF) | (alpha << 24);
                syncPicker(c);
                setLook(cs, index, target.withColor(c));
            }
        }
        return y + sqH + 6;
    }

    private void syncPicker(int color) {
        float[] hsv = Ui.toHsv(color);
        if (hsv[1] > 0 && hsv[2] > 0) hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        pickerColor = color;
    }

    // ---------------------------------------------------------------- footer

    private void footer(int x, int y, int w) {
        boolean dirty = !draft.equals(saved);
        Config.KeyBinds k = draft.keys();
        int bw = 50;
        // Buttons first, so their tooltips are known when the hint line is drawn.
        if (ui.button("done", x + w - bw, y, bw, 14, "Done", Ui.Style.PRIMARY, true, "Save and close (Esc)")) done();
        if (ui.button("apply", x + w - 2 * bw - 4, y, bw, 14, "Apply", Ui.Style.NORMAL, dirty, "Save and show the result")) apply();
        if (ui.button("cancel", x + w - 3 * bw - 8, y, bw, 14, dirty ? "Discard" : "Close", Ui.Style.NORMAL, true,
                dirty ? "Close without saving" : "Close")) closed = true;
        String hint = ui.hint() != null ? ui.hint()
                : k.toggle + " on/off   " + k.cycleProfile + " next profile   " + k.settings + " this screen";
        ui.text(ui.ellipsize(hint, w - 3 * (bw + 4) - 8), x, y + 4, ui.hint() != null ? Ui.TEXT : Ui.FAINT);
    }

    private void apply() {
        sc.apply(draft);
        saved = draft;
    }

    private void done() {
        if (renaming) finishRename();
        if (!draft.equals(saved)) apply();
        closed = true;
    }

    // ---------------------------------------------------------------- draft editing

    private Profile profile() { return draft.activeProfile(); }

    private void setDraft(Config c) {
        draft = c;
        preview = Policy.of(c.activeProfile());
    }

    private void setProfile(Profile p) { setDraft(draft.withProfile(p)); }

    private void setCategory(CategorySettings cs) { setProfile(profile().with(category, cs)); }

    /** Sets the global look ({@code index} -1) or a group's own look. */
    private void setLook(CategorySettings cs, int index, Appearance a) {
        if (index < 0) setCategory(cs.withAppearance(a));
        else setRule(cs, index, cs.rules().get(index).withAppearance(a));
    }

    private void setRule(CategorySettings cs, int index, Rule r) {
        List<Rule> rules = new ArrayList<Rule>(cs.rules());
        rules.set(index, r);
        setCategory(cs.withRules(rules));
    }

    // ---------------------------------------------------------------- labels

    /** The chip shown for an appearance: its color, the texture's own color, or null for hidden. */
    private static Integer chipColor(Appearance a, int original) {
        switch (a.mode()) {
            case SOLID:
            case TEXTURE: return a.color();
            case AVERAGE: return original | 0xFF000000;
            case GRAYSCALE:
                int l = (Colors.red(original) * 299 + Colors.green(original) * 587 + Colors.blue(original) * 114) / 1000;
                return Colors.argb(255, l, l, l);
            case HIDDEN: return null;
            default: return original;
        }
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(sb.length() == 0 ? "" : ", ").append(p);
        return sb.toString();
    }

    /** Vanilla ids without the namespace; modded ids keep it. */
    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static boolean hasGroups(Category c) {
        return c != Category.SKY && c != Category.CLOUDS && c != Category.WEATHER;
    }

    private static Appearance.Mode[] modes(Category c) {
        switch (c) {
            case SKY: return new Appearance.Mode[]{Appearance.Mode.ORIGINAL, Appearance.Mode.SOLID};
            case CLOUDS: return new Appearance.Mode[]{Appearance.Mode.ORIGINAL, Appearance.Mode.HIDDEN};
            case WEATHER: return new Appearance.Mode[]{Appearance.Mode.ORIGINAL, Appearance.Mode.SOLID, Appearance.Mode.HIDDEN};
            default: return Appearance.Mode.values();
        }
    }

    private static String modeLabel(Appearance.Mode m) {
        switch (m) {
            case ORIGINAL: return "Vanilla";
            case SOLID: return "Color";
            case AVERAGE: return "Average";
            case GRAYSCALE: return "Gray";
            case TEXTURE: return "Image";
            default: return "Hide";
        }
    }

    private static String modeTip(Appearance.Mode m) {
        switch (m) {
            case ORIGINAL: return "Leave as it is";
            case SOLID: return "One flat color";
            case AVERAGE: return "Each texture becomes its own average color";
            case GRAYSCALE: return "Keep detail, remove color";
            case TEXTURE: return "Your own image from config/plainview/textures";
            default: return "Make it invisible";
        }
    }

    static String name(Category c) {
        switch (c) {
            case BLOCKS: return "Blocks";
            case FLUIDS: return "Fluids";
            case BLOCK_ENTITIES: return "Block entities";
            case ITEMS: return "Items";
            case ENTITIES: return "Mobs & entities";
            case PLAYERS: return "Players";
            case PARTICLES: return "Particles";
            case BLOCK_BREAKING: return "Mining cracks";
            case SKY: return "Sky";
            case CLOUDS: return "Clouds";
            case WEATHER: return "Weather";
            default: return c.key();
        }
    }

    static String description(Category c) {
        switch (c) {
            case BLOCKS: return "Terrain: stone, grass, leaves, glass...";
            case FLUIDS: return "Water and lava";
            case BLOCK_ENTITIES: return "Chests, beds, signs, banners, paintings";
            case ITEMS: return "Item icons and held items";
            case ENTITIES: return "Mobs, projectiles, armor";
            case PLAYERS: return "Default player skins";
            case PARTICLES: return "Smoke, flames, hits, splashes";
            case BLOCK_BREAKING: return "The crack overlay while mining";
            case SKY: return "Sky color; hides sun, moon and stars";
            case CLOUDS: return "Clouds";
            case WEATHER: return "Rain and snow";
            default: return "";
        }
    }
}
