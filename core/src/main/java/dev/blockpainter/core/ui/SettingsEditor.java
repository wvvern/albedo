package dev.blockpainter.core.ui;

import dev.blockpainter.core.BlockPainter;
import dev.blockpainter.core.Category;
import dev.blockpainter.core.Colors;
import dev.blockpainter.core.Glob;
import dev.blockpainter.core.PixelBuffer;
import dev.blockpainter.core.Policy;
import dev.blockpainter.core.config.Appearance;
import dev.blockpainter.core.config.CategorySettings;
import dev.blockpainter.core.config.Config;
import dev.blockpainter.core.config.ConfigCodec;
import dev.blockpainter.core.config.Profile;
import dev.blockpainter.core.config.Rule;
import dev.blockpainter.core.config.Toggle;
import dev.blockpainter.core.texture.PixelTransformer;
import dev.blockpainter.core.texture.TextureKey;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The settings screen, version-agnostic. Adapters wrap it in their version's Screen class, forward input and draw
 * it through a {@link Canvas}. It edits a draft {@link Config}; Apply/Done hand the draft to {@link BlockPainter#apply}.
 *
 * <p>Pages: a category has a global look, ordered groups (each with its own look) and a texture browser; the World
 * page holds the profile's toggles.
 *
 * <p>Layout: a centered panel (at most {@link #MAX_W} x {@link #MAX_H}) with a header, a sidebar, the page and a
 * footer. Rows of controls are {@link Ui#H} high with {@link #GAP} between them; labels left of controls share one
 * column as wide as the widest label.
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
    /** Texture-name endings that are faces or states of one block, so "grass_block*" can stand for all of them. */
    private static final List<String> FACE_SUFFIXES = Arrays.asList("top", "side", "bottom", "front", "back", "end",
            "overlay", "snow", "inner", "outer", "still", "flow", "base", "open", "on", "off", "lit", "particle");
    /** The labels left of controls; the label column is as wide as the widest. */
    private static final String[] ROW_LABELS = {"Style", "Color", "Shape", "Image", "Blocks", "Scope"};
    private static final int ROW = 11;
    private static final int GAP = 6;
    private static final int MAX_SUGGESTIONS = 6;
    private static final int MAX_W = 620, MAX_H = 380;
    private static final long STATUS_MS = 6000;

    private enum Tab { LOOK, GROUPS, TEXTURES }

    private final BlockPainter sc;
    final Ui ui;
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
    /** The result of the last export or import, shown for a few seconds above the footer. */
    private String status;
    private int statusColor;
    private long statusAt;
    private int suggestion;
    /** Width of the label column ("Style", "Color", ...) plus its gap; set each frame from the font. */
    private int labelW;
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

    public SettingsEditor(final BlockPainter sc) {
        this.sc = sc;
        this.ui = new Ui(new Ui.Clipboard() {
            @Override
            public String get() { return sc.platform().clipboard(); }
        });
        this.saved = sc.config();
        setDraft(saved);
    }

    // ---------------------------------------------------------------- input from adapters (the pointer position
    // is taken from each render call)

    public void mousePressed(double x, double y, int button) {
        if (button == 0) ui.mousePressed();
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
        Recorder recorder = null;
        if (scriptPointer != null) {
            mouseX = scriptPointer[0];
            mouseY = scriptPointer[1];
        }
        boolean release = false;
        if (!SCRIPT.isEmpty() || scriptPointer != null) {
            if (lastTexts != null) release = runScript(SCRIPT.poll());
            if (scriptPointer != null) {
                mouseX = scriptPointer[0];
                mouseY = scriptPointer[1];
            }
            canvas = recorder = new Recorder(canvas);
        }

        ui.begin(canvas, mouseX, mouseY);
        if (ui.clicked()) status = null; // the next click dismisses the message
        int w = canvas.width(), h = canvas.height();
        labelW = 0;
        for (String l : ROW_LABELS) labelW = Math.max(labelW, ui.width(l));
        labelW += 8;
        int pw = Math.min(w - 2 * (w < 440 ? 4 : 10), MAX_W), ph = Math.min(h - 2 * (h < 260 ? 4 : 10), MAX_H);
        int x0 = (w - pw) / 2, y0 = (h - ph) / 2, x1 = x0 + pw, y1 = y0 + ph;
        ui.fill(0, 0, w, h, Ui.SCRIM);
        ui.round(x0, y0, pw, ph, Ui.BG);

        header(x0 + 8, y0 + 6, pw - 16);
        int bodyTop = y0 + 26, bodyBottom = y1 - 24;
        ui.fill(x0 + 8, bodyTop - 3, pw - 16, 1, Ui.BORDER);
        int widest = 0;
        for (Category c : CATEGORIES) widest = Math.max(widest, ui.width(name(c)));
        int sideW = Math.min(widest + 22, pw / 4 + 20);
        sidebar(x0 + 8, bodyTop + 2, sideW, bodyBottom - bodyTop - 4);
        int cx = x0 + 16 + sideW, cw = pw - 24 - sideW;
        content(cx, bodyTop + 2, cw, bodyBottom - bodyTop - 4);
        ui.fill(x0 + 8, bodyBottom + 1, pw - 16, 1, Ui.BORDER);
        footer(x0 + 8, bodyBottom + 5, pw - 16);
        toast(cx, bodyBottom - 3, cw);

        if (ui.takeKey(UiKey.ESCAPE)) {
            if (ui.disarm()) {
                // Esc cancels a pending delete first.
            } else if (group >= 0) openGroup(-1);
            else done();
        }
        ui.end();
        if (release) ui.mouseReleased();
        if (recorder != null) lastTexts = recorder.texts;
    }

    // ---------------------------------------------------------------- header: title, on/off, profiles

    private void header(int x, int y, int w) {
        int bw = 0;
        for (String l : new String[]{"Copy", "Rename", "Delete"}) bw = Math.max(bw, ui.buttonWidth(l));
        int right = x + w, buttonsW = (renaming ? 2 : 3) * (bw + 3) - 3;

        // Title, then the switch. The title is left out first when the window is too narrow for it and the profile.
        int stateW = 22 + ui.width("Off"), titleW = ui.width(BlockPainter.NAME) + 8;
        boolean showTitle = w - 3 * (bw + 3) - 5 - titleW - stateW - 8 >= 70;
        int tx = x;
        if (showTitle) {
            ui.text(BlockPainter.NAME, x, y + 3, Ui.TEXT);
            tx += titleW;
        }
        boolean on = ui.toggle("enabled", tx, y + 2, draft.enabled(),
                (showTitle ? "" : BlockPainter.NAME + ". ") + "Turn the mod on or off (" + draft.keys().toggle + ")");
        if (on != draft.enabled()) setDraft(draft.withEnabled(on));
        ui.text(on ? "On" : "Off", tx + 22, y + 3, on ? Ui.TEXT : Ui.MUTED);

        int px = tx + stateW + 8, pw = right - buttonsW - 8 - px;
        boolean single = draft.profiles().size() <= 1;
        if (renaming) {
            if (ui.isFocused("rename") && ui.takeKey(UiKey.ESCAPE)) {
                cancelRename();
                return;
            }
            boolean save = ui.button("rename.save", right - 2 * bw - 3, y, bw, Ui.H, "Save", Ui.Style.PRIMARY, true, "Save the name (Enter)");
            boolean cancel = ui.button("rename.cancel", right - bw, y, bw, Ui.H, "Cancel", Ui.Style.NORMAL, true, "Keep the old name (Esc)");
            if (cancel) {
                cancelRename();
                return;
            }
            if (pw >= 40) ui.textField("rename", rename, px, y, pw, Ui.H, "Profile name", "Letters, digits, - and _");
            if (save || !ui.isFocused("rename")) finishRename();
            return;
        }

        if (single) ui.button("delete", right - bw, y, bw, Ui.H, "Delete", Ui.Style.DANGER, false, "You cannot delete the last profile");
        else if (ui.confirmButton("delete", right - bw, y, bw, Ui.H, "Delete", "Delete this profile",
                "Click again to delete profile \"" + draft.activeProfileName() + "\"")) {
            String gone = draft.activeProfileName();
            setDraft(draft.withoutProfile(gone));
            setStatus("Deleted profile " + gone + ".", Ui.MUTED);
        }
        if (ui.button("rename", right - 2 * bw - 3, y, bw, Ui.H, "Rename", Ui.Style.NORMAL, true, "Rename this profile")) {
            startRename();
        }
        if (ui.button("new", right - 3 * bw - 6, y, bw, Ui.H, "Copy", Ui.Style.NORMAL, true,
                "Make a new profile as a copy of this one")) {
            String name = draft.freeProfileName(draft.activeProfileName() + "-copy");
            setDraft(draft.withProfile(draft.activeProfile().renamed(name)).withActiveProfile(name));
            startRename();
        }

        if (pw < 40) return;
        String tip = "The active profile (" + (indexOfProfile() + 1) + " of " + draft.profiles().size() + "). "
                + draft.keys().cycleProfile + " selects the next profile in game.";
        if (ui.button("prev", px, y, 13, Ui.H, "<", Ui.Style.GHOST, !single, "Previous profile")) {
            setDraft(draft.withActiveProfile(draft.profileNameAt(-1)));
        }
        if (ui.button("next", px + pw - 13, y, 13, Ui.H, ">", Ui.Style.GHOST, !single, "Next profile")) {
            setDraft(draft.withActiveProfile(draft.profileNameAt(1)));
        }
        ui.round(px + 14, y, pw - 28, Ui.H, Ui.CARD);
        String full = "Profile: " + draft.activeProfileName();
        String label = ui.width(full) <= pw - 32 ? full : ui.ellipsize(draft.activeProfileName(), pw - 32);
        ui.textCentered(label, px + pw / 2, y + 3, Ui.TEXT);
        ui.tip(px + 14, y, pw - 28, Ui.H, label.equals(full) ? tip : full + ". " + tip);
    }

    private int indexOfProfile() {
        return new ArrayList<String>(draft.profiles().keySet()).indexOf(draft.activeProfileName());
    }

    private void startRename() {
        renaming = true;
        rename.set(draft.activeProfileName());
        rename.selectAll = true;
        ui.focus("rename");
    }

    private void finishRename() {
        renaming = false;
        ui.clearFocus();
        setDraft(draft.withRenamedProfile(draft.activeProfileName(), rename.text));
    }

    private void cancelRename() {
        renaming = false;
        ui.clearFocus();
    }

    // ---------------------------------------------------------------- sidebar: categories, then the World page

    private void sidebar(int x, int y, int w, int h) {
        // Rows shrink a little on short windows so the World entry always shows.
        int pitch = Math.max(10, Math.min(13, (h - 6) / (CATEGORIES.length + 1)));
        int ry = y;
        for (Category c : CATEGORIES) {
            Appearance a = profile().get(c).appearance();
            String tip = description(c) + (a.isOriginal() ? " Now: vanilla." : "");
            if (sidebarRow("cat." + c.key(), x, ry, w, pitch - 1, name(c), tip, !worldPage && c == category, !a.isOriginal())) {
                category = c;
                worldPage = false;
                tab = Tab.LOOK;
                notice = null;
                openGroup(-1);
                search.set("");
                ui.resetScroll("list");
            }
            lookIcon(x + 6, ry + (pitch - 9) / 2, a);
            ry += pitch;
        }
        ry += 5;
        ui.fill(x + 4, ry - 3, w - 8, 1, Ui.BORDER);
        if (sidebarRow("world", x, ry, w, pitch - 1, "World", "Fullbright, shading, foliage and shadows", worldPage, true)) {
            worldPage = true;
            openGroup(-1);
        }
        // a small sun as the World icon
        int iy = ry + (pitch - 9) / 2;
        ui.fill(x + 9, iy, 2, 8, Ui.MUTED);
        ui.fill(x + 6, iy + 3, 8, 2, Ui.MUTED);
        ui.fill(x + 8, iy + 2, 4, 4, Ui.TEXT);
    }

    /** An 8x8 icon for a look: its color, or an empty outline for vanilla. */
    private void lookIcon(int x, int y, Appearance a) {
        if (a.isOriginal()) ui.outline(x, y, 8, 8, Ui.FAINT);
        else ui.swatch(x, y, 8, 8, chipColor(a, 0xFF808080));
    }

    /** A selectable sidebar entry; the icon is drawn by the caller at x + 6. Returns true when clicked. */
    private boolean sidebarRow(String id, int x, int y, int w, int h, String label, String tip, boolean active, boolean bright) {
        boolean clicked = false;
        String shown = ui.ellipsize(label, w - 20);
        if (!shown.equals(label)) tip = label + ". " + tip;
        if (active) {
            ui.round(x, y, w, h, Ui.CARD);
            ui.fill(x, y + 2, 2, h - 4, Ui.ACCENT);
            ui.tip(x, y, w, h, tip);
        } else {
            clicked = ui.button(id, x, y, w, h, "", Ui.Style.GHOST, true, tip);
        }
        ui.text(shown, x + 18, y + (h - Ui.LINE) / 2 + 1, active || bright ? Ui.TEXT : Ui.MUTED);
        return clicked;
    }

    // ---------------------------------------------------------------- content

    private void content(int x, int y, int w, int h) {
        if (worldPage) {
            worldPage(x, y, w, h);
            return;
        }
        CategorySettings cs = profile().get(category);
        if (group >= 0 && group < cs.rules().size()) {
            groupPage(x, y, w, y + h, cs, group);
            return;
        }
        group = -1;
        ui.text(name(category), x, y, Ui.TEXT);
        int nameW = ui.width(name(category)) + 8;
        ui.textFit(description(category), x + nameW, y, w - nameW, Ui.MUTED);
        int cy = y + 13;
        if (category.hasGroups()) {
            Tab[] tabs = Tab.values();
            String[] labels = {"Look", "Groups " + cs.rules().size(), "Textures " + sc.catalog(category).size()};
            int picked = ui.tabs("tab", x, cy, w, labels, tab.ordinal(), new String[]{
                    "The look of all " + name(category).toLowerCase(Locale.ROOT) + " textures that are not in a group",
                    "Sets of textures that have their own look",
                    "All loaded textures. Click one to put it in a group."});
            if (picked != tab.ordinal()) {
                tab = tabs[picked];
                notice = null;
                return;
            }
            cy += Ui.H + GAP;
        }
        if (tab == Tab.LOOK || !category.hasGroups()) lookTab(x, cy, w, y + h, cs);
        else if (tab == Tab.GROUPS) groupsTab(x, cy, w, y + h - cy, cs);
        else texturesTab(x, cy, w, y + h - cy, cs);
    }

    private void lookTab(int x, int y, int w, int bottom, CategorySettings cs) {
        int below = (cs.onlyGroups() ? 2 * (Ui.LINE + 1) + GAP : 0)
                + (category == Category.BLOCKS || category == Category.FLUIDS || category == Category.SKY ? 12 : 0);
        int cy = lookEditor(x, y, w, bottom - below, cs, -1);
        if (cs.onlyGroups()) {
            cy += ui.paragraph("Only groups change (see Groups). Groups set to Global use this look.", x, cy, w, Ui.FAINT) + GAP;
        }
        if (category == Category.BLOCKS || category == Category.FLUIDS) {
            boolean tint = ui.toggleRow("tint", x - 4, cy, w + 4, "Remove biome tint", cs.removeTint(),
                    category == Category.BLOCKS ? "Grass and leaves do not get the biome color" : "Water does not get the biome color");
            if (tint != cs.removeTint()) setCategory(cs.withRemoveTint(tint));
        }
        if (category == Category.SKY) ui.paragraph("This also sets the fog to the sky color.", x, cy, w, Ui.FAINT);
    }

    /**
     * Style, color and shape controls for the global look ({@code index} -1) or a group. Returns the next y.
     * A group's first style is "Global" when only groups change, otherwise "Vanilla" (which keeps its blocks as they are).
     */
    private int lookEditor(int x, int y, int w, int bottom, CategorySettings cs, int index) {
        Rule rule = index < 0 ? null : cs.rules().get(index);
        boolean global = rule != null && rule.usesGlobalLook();
        Appearance target = rule == null ? cs.appearance() : rule.appearance();
        List<Appearance.Mode> modes = category.supportedModes();
        String[] labels = new String[modes.size()], tips = new String[modes.size()];
        int selected = 0;
        for (int i = 0; i < modes.size(); i++) {
            labels[i] = modeLabel(modes.get(i));
            tips[i] = modeTip(modes.get(i));
            if (!global && modes.get(i) == target.mode()) selected = i;
        }
        if (rule != null && cs.onlyGroups()) {
            labels[0] = "Global";
            tips[0] = "Use the " + name(category) + " look";
        } else if (rule != null) {
            tips[0] = "Do not change these blocks";
        }
        int lx = x + labelW, cw = w - labelW;
        ui.text("Style", x, y + 3, Ui.MUTED);
        int picked = ui.segmented("mode", lx, y, cw, Ui.H, labels, selected, tips);
        int cy = y + ui.lastHeight + GAP;
        if (picked != selected) {
            if (rule != null && cs.onlyGroups() && picked == 0) setRule(cs, index, rule.withGlobalLook());
            else setLook(cs, index, target.withMode(modes.get(picked)));
            return cy; // the rest of the layout depends on the style; draw it next frame
        }
        if (global) {
            ui.swatch(lx, cy + 2, 9, 9, chipColor(cs.appearance(), 0xFF808080));
            ui.text(ui.ellipsize(modeTip(cs.appearance().mode()) + " (the " + name(category) + " look)", cw - 14), lx + 13, cy + 3, Ui.MUTED);
            return cy + Ui.H + GAP;
        }
        boolean usesShape = target.mode() != Appearance.Mode.ORIGINAL && target.mode() != Appearance.Mode.HIDDEN
                && category != Category.SKY && category != Category.CLOUDS;
        if (target.mode() == Appearance.Mode.TEXTURE) cy = texturePicker(x, cy, w, cs, index, target);
        if (target.mode() == Appearance.Mode.SOLID || target.mode() == Appearance.Mode.TEXTURE) {
            ui.text("Color", x, cy + 3, Ui.MUTED);
            if (target.mode() == Appearance.Mode.TEXTURE) ui.tip(x, cy, labelW, Ui.H, "The color to use when the image is not found");
            // The picker gives up height first when the window is short, so the rows below it stay on screen.
            int pickerH = Math.max(24, Math.min(44, bottom - cy - GAP - (usesShape ? Ui.H + GAP : 0)));
            cy = colorPicker(lx, cy, cw, pickerH, cs, index, target);
        }
        if (usesShape) {
            ui.text("Shape", x, cy + 3, Ui.MUTED);
            int a = ui.segmented("alpha", lx, cy, cw, Ui.H, new String[]{"Keep", "Cut-out", "Solid"}, target.alpha().ordinal(),
                    new String[]{"Keep transparent parts (glass, leaves)",
                            "Make each part fully solid or fully transparent",
                            "Fill all holes. Leaves and glass become solid cubes."});
            if (a != target.alpha().ordinal()) setLook(cs, index, target.withAlpha(Appearance.Alpha.values()[a]));
            cy += ui.lastHeight + GAP;
        }
        return cy;
    }

    // ---------------------------------------------------------------- groups

    private void groupsTab(int x, int y, int w, int h, CategorySettings cs) {
        ui.text("Scope", x, y + 3, Ui.MUTED);
        int scope = ui.segmented("scope", x + labelW, y, w - labelW, Ui.H, new String[]{"Everything", "Only groups"},
                cs.onlyGroups() ? 1 : 0, new String[]{
                        "The " + name(category) + " look applies to all. Groups change their own blocks.",
                        "Only blocks in groups change. All other blocks stay vanilla."});
        int cy = y + ui.lastHeight + GAP;
        if (scope != (cs.onlyGroups() ? 1 : 0)) {
            Rule blocker = cs.blocksScopeSwitch();
            if (blocker == null) {
                setCategory(cs.withOnlyGroups(scope == 1));
                notice = null;
            } else {
                notice = "Group \"" + blocker.displayName() + "\" uses " + (cs.onlyGroups() ? "Global" : "Vanilla")
                        + ". This has no meaning in the other scope. Change it first.";
            }
            return;
        }
        if (notice != null) cy += ui.paragraph(notice, x, cy, w, Ui.DANGER) + GAP;
        String newLabel = "+ New group";
        int nw = ui.buttonWidth(newLabel);
        if (ui.button("group.new", x, cy, nw, Ui.H, newLabel, Ui.Style.PRIMARY, true, "Make a set of blocks with its own look")) {
            setCategory(cs.withRuleAt(cs.rules().size(), cs.newGroup(freeGroupName(cs), Collections.<String>emptyList())));
            openGroup(cs.rules().size());
            ui.focus("add");
            return;
        }
        List<Rule> rules = cs.rules();
        if (rules.size() > 1) ui.textFit("A higher group wins.", x + nw + 6, cy + 3, w - nw - 6, Ui.FAINT);
        cy += Ui.H + GAP;
        if (rules.isEmpty()) {
            ui.paragraph(cs.onlyGroups() ? "There are no groups. Nothing changes."
                    : "There are no groups. All textures use the " + name(category) + " look.", x, cy + 2, w, Ui.FAINT);
            return;
        }
        int rowH = 16, lh = y + h - cy;
        int first = ui.scroll("groups", x, cy, w, lh, rules.size(), rowH);
        int listW = rules.size() > Math.max(1, lh / rowH) ? w - 6 : w;
        for (int i = first; i < Math.min(rules.size(), first + Math.max(1, lh / rowH)); i++) {
            Rule r = rules.get(i);
            int ry = cy + (i - first) * rowH;
            int bx = x + listW - 50;
            ui.round(x, ry, listW, rowH - 2, Ui.PANEL);
            if (ui.button("group." + i, x, ry, bx - x - 2, rowH - 2, "", Ui.Style.GHOST, true, "Edit group " + r.displayName())) {
                openGroup(i);
                return;
            }
            Appearance a = cs.lookOf(r);
            lookIcon(x + 4, ry + 3, a);
            String look = r.usesGlobalLook() ? "global look" : a.isOriginal() ? "vanilla" : modeLabel(a.mode()).toLowerCase(Locale.ROOT);
            int room = bx - x - 22;
            int nameW = Math.min(ui.width(r.displayName()), room / 2);
            ui.text(ui.ellipsize(r.displayName(), nameW), x + 16, ry + 3, Ui.TEXT);
            String members = r.patterns().isEmpty() ? "no blocks" : String.join(", ", r.patterns());
            ui.text(ui.ellipsize(look + ": " + members, room - nameW - 6), x + 22 + nameW, ry + 3, Ui.MUTED);
            if (ui.button("group.up." + i, bx, ry + 1, 15, 12, "^", Ui.Style.GHOST, i > 0, "Move up. A higher group wins.")) {
                setCategory(cs.withRuleMoved(i, -1));
            }
            if (ui.button("group.down." + i, bx + 16, ry + 1, 15, 12, "v", Ui.Style.GHOST, i < rules.size() - 1, "Move down")) {
                setCategory(cs.withRuleMoved(i, 1));
            }
            if (ui.confirmButton("group.del." + i, bx + 34, ry + 1, 15, 12, "x", "Delete this group",
                    "Click again to delete \"" + r.displayName() + "\"")) {
                setCategory(cs.withoutRule(i));
                return;
            }
        }
    }

    private void groupPage(int x, int y, int w, int bottom, CategorySettings cs, int index) {
        Rule rule = cs.rules().get(index);
        if (ui.button("back", x, y - 3, 13, Ui.H, "<", Ui.Style.GHOST, true, "Back to the groups (Esc)")) {
            openGroup(-1);
            return;
        }
        String crumb = name(category) + " /";
        ui.text(crumb, x + 16, y, Ui.MUTED);
        int dw = ui.buttonWidth("Delete");
        int nx = x + 20 + ui.width(crumb), nw = Math.min(140, x + w - dw - 6 - nx);
        if (!ui.isFocused("group.name")) groupName.set(rule.displayName());
        if (ui.textField("group.name", groupName, nx, y - 3, nw, Ui.H, "Group name", "The name of this group. Click to change it.")
                && !groupName.text.trim().isEmpty()) {
            setRule(cs, index, rule.withName(groupName.text.trim()));
            return;
        }
        if (ui.confirmButton("group.delete", x + w - dw, y - 3, dw, Ui.H, "Delete", "Delete this group",
                "Click again to delete \"" + rule.displayName() + "\"")) {
            setCategory(cs.withoutRule(index));
            openGroup(-1);
            return;
        }
        // Keep room for the block input and one line of chips under the look.
        int cy = lookEditor(x, y + Ui.H + 2, w, bottom - Ui.H - 16, cs, index);
        blocksEditor(x, cy, w, bottom, cs, index);
    }

    /** The group's blocks as removable chips, and an input that suggests matching textures while typing. */
    private void blocksEditor(int x, int y, int w, int bottom, CategorySettings cs, int index) {
        Rule rule = cs.rules().get(index);
        Map<String, Integer> catalog = sc.catalog(category);
        ui.text("Blocks", x, y + 3, Ui.MUTED);
        int fx = x + labelW, fw = w - labelW;
        // Keys the list reacts to are taken before the field would (Enter would unfocus it); typing goes to the field.
        boolean down = false, up = false, complete = false, enter = false;
        if (ui.isFocused("add")) {
            down = ui.takeKey(UiKey.DOWN);
            up = ui.takeKey(UiKey.UP);
            complete = ui.takeKey(UiKey.TAB);
            enter = ui.takeKey(UiKey.ENTER);
        }
        if (ui.textField("add", add, fx, y, fw, Ui.H, "Type a block name, for example dirt",
                "Enter adds the selected match. Tab completes. * matches any text.")) {
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
        int listY = y + Ui.H + 2, rows = Math.min(list.size(), Math.max(0, (bottom - listY - 2) / ROW));
        if (rows > 0) ui.reserveOverlay(fx, listY, fw, rows * ROW + 2);
        if (ui.isFocused("add") && !add.text.trim().isEmpty() && list.isEmpty()) {
            ui.textFit("No texture matches. Enter adds it as you typed it.", fx, listY + 2, fw, Ui.FAINT);
            listY += 12;
        }

        // chips
        int cx = fx, cyy = listY + 2;
        for (int i = 0; i < rule.patterns().size(); i++) {
            String p = rule.patterns().get(i);
            int[] stats = chipStats(cs, index, p, catalog);
            int matched = stats[0], effective = stats[1];
            Integer color = matched > 0 ? stats[2] : null;
            boolean dim = matched == 0 || effective == 0;
            String tip = matched == 0 ? "No loaded texture has the name " + p
                    : effective == 0 ? "A higher group already has all " + matched + " of these textures"
                    : effective + " texture" + (effective == 1 ? "" : "s");
            int cw = ui.chipWidth(p, color != null);
            if (cx + cw > fx + fw && cx > fx) {
                cx = fx;
                cyy += 14;
            }
            // Keep a line free for "+N more" unless this is the last chip, so none disappear silently.
            boolean last = i == rule.patterns().size() - 1;
            if (cyy + 12 > (last ? bottom : bottom - 11)) {
                int more = rule.patterns().size() - i;
                if (cyy + Ui.LINE <= bottom && !ui.textFit("+" + more + " more. Make the window larger to see them.", fx, cyy + 2, fw, Ui.FAINT)) {
                    ui.textFit("+" + more + " more", fx, cyy + 2, fw, Ui.FAINT);
                }
                break;
            }
            boolean[] removed = {false};
            cx += ui.chip("chip." + i + "." + p, cx, cyy, color, p, dim, tip, removed) + 3;
            if (removed[0]) {
                List<String> next = new ArrayList<String>(rule.patterns());
                next.remove(i);
                setRule(cs, index, rule.withPatterns(next));
                return;
            }
        }
        if (rule.patterns().isEmpty() && rows == 0 && !ui.isFocused("add")) {
            ui.paragraph("No blocks yet. Type a block name above.", fx, cyy + 2, fw, Ui.FAINT);
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
                String right = s.owner != null ? "in " + s.owner : s.count > 1 ? s.count + " textures" : "";
                int rw = right.isEmpty() ? 0 : Math.min(ui.width(right), fw / 3) + 6;
                ui.text(ui.ellipsize(s.pattern, fw - 20 - rw), fx + 15, ry + 1, Ui.TEXT);
                if (rw > 0) ui.text(ui.ellipsize(right, rw - 6), fx + fw - rw - 2, ry + 1, s.owner != null ? Ui.WARN : Ui.FAINT);
                if (s.owner != null) ui.tip(fx + 1, ry, fw - 2, ROW, "Group " + s.owner + " also has this. A higher group wins.");
            }
            ui.endOverlay();
        }
    }

    // Scanning ~1000 textures with globs is too slow to do every frame. Results depend only on the groups' block
    // lists and the catalog, so they're kept until one of those changes (not on every color or name edit).
    private List<List<String>> memoPatterns;
    private Map<String, Integer> memoCatalog;
    private TreeSet<String> shortIds;
    private final Map<String, int[]> chipMemo = new HashMap<String, int[]>();
    private final Map<String, List<Suggestion>> suggestionMemo = new HashMap<String, List<Suggestion>>();

    private void checkMemo(CategorySettings cs, Map<String, Integer> catalog) {
        List<List<String>> patterns = new ArrayList<List<String>>();
        for (Rule r : cs.rules()) patterns.add(r.patterns());
        if (catalog != memoCatalog) {
            memoCatalog = catalog;
            shortIds = new TreeSet<String>();
            for (String id : catalog.keySet()) shortIds.add(shortId(id));
            memoPatterns = null;
        }
        if (!patterns.equals(memoPatterns)) {
            memoPatterns = patterns;
            chipMemo.clear();
            suggestionMemo.clear();
        }
    }

    /** {textures matched, textures group {@code index} actually gets (no higher group took them), first match's color}. */
    private int[] chipStats(CategorySettings cs, int index, String pattern, Map<String, Integer> catalog) {
        checkMemo(cs, catalog);
        String key = index + "|" + pattern;
        int[] stats = chipMemo.get(key);
        if (stats != null) return stats;
        Glob g = Glob.compile(pattern);
        Rule rule = cs.rules().get(index);
        stats = new int[3];
        for (Map.Entry<String, Integer> e : catalog.entrySet()) {
            if (!g.matches(e.getKey())) continue;
            if (stats[0]++ == 0) stats[2] = e.getValue();
            if (cs.ruleFor(e.getKey()) == rule) stats[1]++;
        }
        chipMemo.put(key, stats);
        return stats;
    }

    private void addPattern(CategorySettings cs, int index, String pattern) {
        add.set("");
        suggestion = 0;
        if (!pattern.isEmpty()) setCategory(cs.withPatternIn(index, pattern));
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
            if (e.getValue().size() > 1 && wholeBlock(e.getKey())) candidates.add(e.getKey() + "*");
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

    /**
     * True when "stem*" matches only that block's own textures (not e.g. "stone*" matching stone_bricks): a prefix
     * range over the sorted names, no regex.
     */
    private boolean wholeBlock(String stem) {
        for (String s : shortIds.subSet(stem, true, stem + Character.MAX_VALUE, true)) {
            if (!stem(s).equals(stem)) return false;
        }
        return true;
    }

    private static String cleanPattern(String text) {
        return shortId(text.trim().toLowerCase(Locale.ROOT));
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
        int sw = Math.min(150, w);
        if (ui.textField("search", search, x, y, sw, Ui.H, "Search textures", "Type a name, for example leaves or ore")) {
            ui.resetScroll("list");
        }
        if (!q.isEmpty()) ui.textFit(ids.size() + " of " + catalog.size(), x + sw + 6, y + 3, w - sw - 6, Ui.MUTED);
        int ly = y + Ui.H + GAP, lh = h - Ui.H - GAP;
        if (lh < 20) return;
        ui.round(x, ly, w, lh, Ui.PANEL);
        if (catalog.isEmpty() || ids.isEmpty()) {
            ui.paragraph(catalog.isEmpty() ? "Textures show here after the game loads them."
                    : "No texture name has \"" + q + "\".", x + 6, ly + 6, w - 12, Ui.FAINT);
            return;
        }
        int first = ui.scroll("list", x, ly + 2, w, lh - 4, ids.size(), ROW);
        int visible = (lh - 4) / ROW;
        for (int i = first; i < Math.min(ids.size(), first + visible); i++) {
            String id = ids.get(i);
            int ry = ly + 2 + (i - first) * ROW;
            Rule owner = cs.ruleFor(id);
            if (ui.button("row." + id, x + 2, ry, w - 8, ROW, "", Ui.Style.GHOST, true,
                    owner == null ? "Click to make a new group with " + shortId(id) : "Click to open group " + owner.displayName())) {
                openTextureGroup(cs, id, owner);
                return;
            }
            int original = catalog.get(id);
            ui.swatch(x + 5, ry + 2, 7, 7, original);
            ui.text(">", x + 14, ry + 1, Ui.FAINT);
            ui.swatch(x + 20, ry + 2, 7, 7, chipColor(preview.appearanceFor(new TextureKey(category, id)), original));
            String tag = owner == null ? "" : owner.displayName();
            int tagW = tag.isEmpty() ? 0 : Math.min(ui.width(tag), w / 3) + 6;
            ui.text(ui.ellipsize(shortId(id), w - 46 - tagW), x + 31, ry + 1, owner == null ? Ui.MUTED : Ui.TEXT);
            if (!tag.isEmpty()) ui.text(ui.ellipsize(tag, tagW - 6), x + w - 8 - tagW, ry + 1, Ui.ACCENT);
        }
    }

    private void openTextureGroup(CategorySettings cs, String id, Rule owner) {
        if (owner != null) {
            openGroup(cs.rules().indexOf(owner));
        } else {
            // First place, so the texture really gets this group's look even if a broader group also matches it.
            setCategory(cs.withRuleAt(0, cs.newGroup(shortId(id), Collections.singletonList(shortId(id)))));
            openGroup(0);
        }
    }

    // ---------------------------------------------------------------- world page

    private void worldPage(int x, int y, int w, int h) {
        ui.text("World", x, y, Ui.TEXT);
        int nameW = ui.width("World") + 8;
        ui.textFit("These settings apply to all categories", x + nameW, y, w - nameW, Ui.MUTED);
        int cy = y + 16;
        // Each row shows its explanation when all rows fit with it; otherwise the explanation is the tooltip only.
        int need = 0;
        for (Toggle t : Toggle.values()) need += 18 + ui.paragraphHeight(explain(t), w - 12) + 4;
        boolean full = need <= y + h - cy;
        for (Toggle t : Toggle.values()) {
            int rh = full ? 18 + ui.paragraphHeight(explain(t), w - 12) : 16;
            ui.round(x, cy, w, rh, Ui.PANEL);
            boolean on = profile().is(t);
            if (ui.toggleRow(t.key, x + 2, cy + 2, w - 4, label(t), on, explain(t)) != on) setProfile(profile().with(t, !on));
            if (full) ui.paragraph(explain(t), x + 6, cy + 15, w - 12, Ui.MUTED);
            cy += rh + 4;
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
            case FULLBRIGHT: return "Full light in all areas. No dark caves or night.";
            case SHADING: return "Smooth lighting and darker block sides.";
            case FOLIAGE: return "Grass, flowers, ferns and vines. Off hides them.";
            default: return "The round shadows below mobs and players.";
        }
    }

    // ---------------------------------------------------------------- color and image pickers

    private int texturePicker(int x, int y, int w, CategorySettings cs, int index, Appearance target) {
        List<String> names = sc.userTextureNames();
        ui.text("Image", x, y + 3, Ui.MUTED);
        int lx = x + labelW, lw = w - labelW;
        if (names.isEmpty()) {
            return y + ui.paragraph("No images. Put PNG files in config/blockpainter/textures.", lx, y + 3, lw, Ui.FAINT) + 3 + GAP;
        }
        int i = Math.max(0, names.indexOf(target.texture()));
        boolean many = names.size() > 1;
        if (ui.button("tex.prev", lx, y, 13, Ui.H, "<", Ui.Style.GHOST, many, "Previous image")) i = (i + names.size() - 1) % names.size();
        if (ui.button("tex.next", lx + lw - 13, y, 13, Ui.H, ">", Ui.Style.GHOST, many, "Next image")) i = (i + 1) % names.size();
        ui.round(lx + 14, y, lw - 28, Ui.H, Ui.CARD);
        ui.textCentered(ui.ellipsize(names.get(i), lw - 32), lx + lw / 2, y + 3, Ui.TEXT);
        ui.tip(lx + 14, y, lw - 28, Ui.H, names.get(i) + " (" + (i + 1) + " of " + names.size() + ")");
        if (!names.get(i).equals(target.texture())) setLook(cs, index, target.withTexture(names.get(i)));
        return y + Ui.H + GAP;
    }

    /** Saturation/brightness square, hue and opacity bars, hex input and a palette. Returns the next y. */
    private int colorPicker(int x, int y, int w, int sqH, CategorySettings cs, int index, Appearance target) {
        int color = target.color();
        if (color != pickerColor) syncPicker(color);
        int alpha = Colors.alpha(color);
        int sqW = Math.min(84, w / 3);

        // saturation (x) / brightness (y) square, drawn in 3px cells
        for (int yy = 0; yy < sqH; yy += 3) {
            for (int xx = 0; xx < sqW; xx += 3) {
                int cell = Ui.hsv(hue, xx / (float) (sqW - 1), 1 - yy / (float) (sqH - 1));
                ui.fill(x + xx, y + yy, Math.min(3, sqW - xx), Math.min(3, sqH - yy), cell);
            }
        }
        int kx = x + Math.round(sat * (sqW - 1)), ky = y + Math.round((1 - val) * (sqH - 1));
        ui.outline(kx - 2, ky - 2, 5, 5, val > 0.5f ? 0xFF000000 : 0xFFFFFFFF);
        ui.tip(x, y, sqW, sqH, "Saturation and brightness");
        float[] sv = ui.dragArea("sv", x, y, sqW, sqH);
        if (sv != null) {
            sat = sv[0];
            val = 1 - sv[1];
        }

        // hue bar
        int hx = x + sqW + 4;
        for (int yy = 0; yy < sqH; yy += 2) ui.fill(hx, y + yy, 8, Math.min(2, sqH - yy), Ui.hsv(yy / (float) sqH, 1, 1));
        ui.fill(hx - 1, y + Math.round(hue * (sqH - 1)), 10, 1, 0xFFFFFFFF);
        ui.tip(hx, y, 8, sqH, "Hue");
        float[] hv = ui.dragArea("hue", hx, y, 8, sqH);
        if (hv != null) hue = Math.min(hv[1], 0.999f);

        // opacity bar
        int ax = hx + 12;
        ui.checker(ax, y, 8, sqH);
        int rgb = Ui.hsv(hue, sat, val);
        for (int yy = 0; yy < sqH; yy += 2) ui.fill(ax, y + yy, 8, Math.min(2, sqH - yy), Colors.withAlpha(rgb, 255 - yy * 255 / sqH));
        ui.fill(ax - 1, y + Math.round((1 - alpha / 255f) * (sqH - 1)), 10, 1, 0xFFFFFFFF);
        ui.tip(ax, y, 8, sqH, "Opacity " + Math.round(alpha * 100 / 255f) + "%");
        float[] av = ui.dragArea("alpha.bar", ax, y, 8, sqH);
        if (av != null) alpha = Math.round((1 - av[1]) * 255);

        // Dragging only the opacity keeps the exact RGB (the HSV round trip could shift it slightly).
        int next = Colors.withAlpha(av != null && sv == null && hv == null ? color : rgb, alpha);
        if (next != color) {
            pickerColor = next;
            setLook(cs, index, target.withColor(next));
        }

        // preview, hex and palette
        // The large preview is left out when it would make the hex field too narrow to show a whole color.
        int px = ax + 14, pw = Math.max(40, x + w - px);
        int hexW = Math.min(62, pw), preview = pw - hexW >= 22 ? 22 : 0;
        if (preview > 0) ui.swatch(px, y, 18, 18, next);
        if (!ui.isFocused("hex")) hex.set(Colors.format(color));
        if (ui.textField("hex", hex, px + preview, y + 2, hexW, Ui.H, "#RRGGBB", "Type a color: #RGB, #RRGGBB or #AARRGGBB")) {
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
            if (sy + sw > y + sqH) break; // rows that do not fit beside the square are left out, never drawn over the next row
            boolean current = (PALETTE[i] & 0xFFFFFF) == (color & 0xFFFFFF);
            boolean clicked = ui.button("pal." + i, sx - 1, sy - 1, sw + 2, sw + 2, "", Ui.Style.GHOST, true, Colors.format(PALETTE[i]));
            if (current) ui.outline(sx - 1, sy - 1, sw + 2, sw + 2, Ui.ACCENT);
            ui.swatch(sx, sy, sw, sw, PALETTE[i]);
            if (clicked) {
                int c = Colors.withAlpha(PALETTE[i], alpha);
                syncPicker(c);
                setLook(cs, index, target.withColor(c));
            }
        }
        return y + sqH + GAP;
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
        int bw = Math.max(44, Math.max(ui.buttonWidth("Discard"), ui.buttonWidth("Apply")));
        int lw = Math.max(ui.buttonWidth("Export"), ui.buttonWidth("Import"));
        if (ui.button("done", x + w - bw, y, bw, Ui.H, "Done", Ui.Style.PRIMARY, true, "Save and close (Esc)")) done();
        if (ui.button("apply", x + w - 2 * bw - 3, y, bw, Ui.H, "Apply", Ui.Style.NORMAL, dirty,
                dirty ? "Save and show the result. The screen stays open." : "There are no changes to save")) apply();
        if (ui.button("cancel", x + w - 3 * bw - 6, y, bw, Ui.H, dirty ? "Discard" : "Close", Ui.Style.NORMAL, true,
                dirty ? "Close and do not save the changes" : "Close")) closed = true;
        if (ui.button("export", x, y, lw, Ui.H, "Export", Ui.Style.NORMAL, true,
                "Save this profile to a file and copy it to the clipboard")) {
            exportProfile();
        }
        if (ui.button("import", x + lw + 3, y, lw, Ui.H, "Import", Ui.Style.NORMAL, true,
                "Add the profile that is on the clipboard")) {
            importProfile();
        }
        int tx = x + 2 * lw + 3 + 8, room = x + w - 3 * bw - 6 - 8 - tx;
        if (dirty) {
            if (!ui.textFit("Changes not saved", tx, y + 3, room, Ui.WARN)) ui.textFit("Not saved", tx, y + 3, room, Ui.WARN);
        } else if (!ui.textFit(k.toggle + " on/off   " + k.cycleProfile + " next profile   " + k.settings + " this screen",
                tx, y + 3, room, Ui.FAINT)) {
            ui.textFit(k.toggle + " on/off   " + k.cycleProfile + " next", tx, y + 3, room, Ui.FAINT);
        }
    }

    /** The last export or import result: a box above the footer for a few seconds, wrapped so all of it shows. */
    private void toast(int x, int bottom, int w) {
        if (status == null) return;
        if (System.currentTimeMillis() - statusAt > STATUS_MS) {
            status = null;
            return;
        }
        int tw = Math.min(w, 240), th = ui.paragraphHeight(status, tw - 12) + 8;
        int ty = bottom - th;
        ui.round(x, ty, tw, th, statusColor == Ui.DANGER ? Ui.DANGER : Ui.BORDER);
        ui.round(x + 1, ty + 1, tw - 2, th - 2, Ui.TOOLTIP);
        ui.paragraph(status, x + 6, ty + 4, tw - 12, statusColor == Ui.DANGER ? Ui.DANGER : Ui.TEXT);
    }

    private void exportProfile() {
        try {
            File file = sc.exportProfile(profile());
            setStatus("Saved to profiles/" + file.getName() + " and copied to the clipboard", Ui.MUTED);
        } catch (IOException e) {
            sc.platform().warn("Cannot export the profile", e);
            setStatus("Cannot save the file: " + e.getMessage(), Ui.DANGER);
        }
    }

    private void importProfile() {
        String text = sc.platform().clipboard();
        if (text == null || text.trim().isEmpty()) {
            setStatus("The clipboard is empty. Copy the text of a profile file, then push Import.", Ui.DANGER);
            return;
        }
        try {
            setDraft(draft.withImportedProfile(ConfigCodec.decodeProfileFile(text)));
            setStatus("Added profile " + draft.activeProfileName() + ". Push Apply or Done to keep it.", Ui.MUTED);
        } catch (IllegalArgumentException e) {
            setStatus("Cannot import: " + e.getMessage(), Ui.DANGER);
        }
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
        statusAt = System.currentTimeMillis();
    }

    private void apply() {
        sc.apply(draft);
        saved = draft;
        setStatus("Saved", Ui.MUTED);
    }

    private void done() {
        if (renaming) finishRename();
        if (!draft.equals(saved)) sc.apply(draft);
        saved = draft;
        closed = true;
    }

    // ---------------------------------------------------------------- draft editing

    private Profile profile() { return draft.activeProfile(); }

    private void setDraft(Config c) {
        // An open group belongs to the profile it was opened in.
        if (draft != null && !draft.activeProfileName().equals(c.activeProfileName())) openGroup(-1);
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
        setCategory(cs.withRule(index, r));
    }

    // ---------------------------------------------------------------- scripted input for the dev harness

    private static final Queue<String> SCRIPT = new ConcurrentLinkedQueue<String>();
    /** Labels drawn in the last frame while a script runs: text -> center. */
    private Map<String, int[]> lastTexts;
    private int[] scriptPointer;

    /**
     * Queues an input for the open settings screen, for screenshots of each page (see the dev harness):
     * {@code click <label>}, {@code type <text>}, {@code key <UiKey>}, {@code pointer off}. One runs per frame.
     */
    public static void script(String command) {
        SCRIPT.add(command);
    }

    /** Returns true when the left button must be released after this frame. */
    private boolean runScript(String command) {
        if (command == null) return false;
        int space = command.indexOf(' ');
        String verb = space < 0 ? command : command.substring(0, space);
        String arg = space < 0 ? "" : command.substring(space + 1);
        if (verb.equals("click")) {
            int[] at = lastTexts.get(arg);
            if (at == null) {
                for (Map.Entry<String, int[]> e : lastTexts.entrySet()) if (e.getKey().contains(arg)) at = e.getValue();
            }
            if (at == null) return false;
            scriptPointer = at;
            ui.mousePressed();
            return true;
        }
        if (verb.equals("type")) for (char ch : arg.toCharArray()) ui.typed(ch);
        else if (verb.equals("key")) ui.key(UiKey.valueOf(arg), false);
        else if (verb.equals("pointer")) scriptPointer = new int[]{-1, -1};
        return false;
    }

    /** Passes drawing through and keeps where each text was drawn. */
    private static final class Recorder implements Canvas {
        final Canvas c;
        final Map<String, int[]> texts = new HashMap<String, int[]>();

        Recorder(Canvas c) { this.c = c; }

        @Override public int width() { return c.width(); }
        @Override public int height() { return c.height(); }
        @Override public void fill(int x0, int y0, int x1, int y1, int argb) { c.fill(x0, y0, x1, y1, argb); }
        @Override public int textWidth(String text) { return c.textWidth(text); }

        @Override
        public void text(String text, int x, int y, int argb) {
            c.text(text, x, y, argb);
            if (!texts.containsKey(text)) texts.put(text, new int[]{x + c.textWidth(text) / 2, y + 4});
        }
    }

    // ---------------------------------------------------------------- labels

    /**
     * The preview color for an appearance applied to a texture whose average is {@code original}: the real transform
     * on one pixel, so previews always match what is rendered. Null means hidden.
     */
    private static Integer chipColor(Appearance a, int original) {
        if (a.mode() == Appearance.Mode.HIDDEN) return null;
        PixelBuffer.Heap pixel = new PixelBuffer.Heap(1, 1, new int[]{original | 0xFF000000});
        PixelTransformer.apply(a.withAlpha(Appearance.Alpha.OPAQUE), pixel, null);
        return pixel.getArgb(0, 0);
    }

    /** Vanilla ids without the namespace; modded ids keep it. */
    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
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
            case ORIGINAL: return "Do not change";
            case SOLID: return "One flat color";
            case AVERAGE: return "Each texture changes to its average color";
            case GRAYSCALE: return "Keep the detail and remove the color";
            case TEXTURE: return "A PNG image from config/blockpainter/textures";
            default: return "Do not show";
        }
    }

    static String name(Category c) {
        switch (c) {
            case BLOCKS: return "Blocks";
            case FLUIDS: return "Fluids";
            case BLOCK_ENTITIES: return "Block entities";
            case ITEMS: return "Items";
            case ENTITIES: return "Mobs and entities";
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
            case BLOCK_BREAKING: return "The cracks on a block while you mine it";
            case SKY: return "Sky color. Also hides the sun, moon and stars.";
            case CLOUDS: return "Clouds";
            case WEATHER: return "Rain and snow";
            default: return "";
        }
    }
}
