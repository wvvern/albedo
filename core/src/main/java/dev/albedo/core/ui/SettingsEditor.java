package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.Colors;
import dev.albedo.core.Glob;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.RefreshResult;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.texture.PixelTransformer;
import dev.albedo.core.texture.TextureKey;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.albedo.core.ui.GroupMatches.shortId;
import static dev.albedo.core.ui.GroupMatches.toPattern;

/** The settings screen of all versions, which edits a draft config that Apply and Done give to {@link Albedo#apply}. */
public final class SettingsEditor {
    private static final Category[] CATEGORIES = {
            Category.BLOCKS, Category.FLUIDS, Category.BLOCK_ENTITIES, Category.DECORATIONS, Category.ITEMS, Category.ENTITIES,
            Category.PLAYERS, Category.PARTICLES, Category.BLOCK_BREAKING, Category.SKY, Category.CLOUDS, Category.WEATHER,
    };
    private static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFC8C8C8, 0xFF8C8C8C, 0xFF505050, 0xFF141414, 0xFF000000, 0xFF6D4C41, 0xFFFF7043,
            0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF7CB342, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA,
    };
    /** The labels left of controls; the label column is as wide as the widest. */
    private static final String[] ROW_LABELS = {"Style", "Color", "Shape", "Image", "Scope"};
    private static final String BUILT_IN = "built-in";
    private static final int ROW = 11;
    private static final int GAP = 6;
    private static final int MAX_W = 620, MAX_H = 380;
    private static final long TOAST_MS = 6000;

    private enum Tab { LOOK, GROUPS, TEXTURES }

    /** The header's mode, which only {@link #setMode} changes, so that each mode gives back the focus it took. */
    private enum Mode {
        BROWSE,
        /** The searchable profile list is open under the profile button. */
        PICK_PROFILE,
        /** The profile name is a text field, with Save and Cancel. */
        RENAME
    }

    /** What the content area shows (the World page, a category tab or a group), which only {@link #go} changes. */
    private static final class Page {
        final Category category;
        final Tab tab;
        final boolean world;
        /** The open group, or -1. */
        final int group;

        private Page(Category category, Tab tab, boolean world, int group) {
            this.category = category;
            this.tab = tab;
            this.world = world;
            this.group = group;
        }

        static Page of(Category c) { return new Page(c, Tab.LOOK, false, -1); }

        /** The World page keeps the category, so that the sidebar can go back to it. */
        Page toWorld() { return new Page(category, tab, true, -1); }

        Page withTab(Tab t) { return new Page(category, t, false, -1); }

        /** Opens a group of the category, or with -1 goes back to the tab it was opened from. */
        Page withGroup(int index) { return new Page(category, tab, world && index < 0, index); }

        boolean inGroup() { return group >= 0; }
    }

    /** A message above the footer for a few seconds; the next click hides it. */
    private static final class Toast {
        final String text;
        final boolean error;
        final long at;

        Toast(String text, boolean error) {
            this.text = text;
            this.error = error;
            this.at = System.currentTimeMillis();
        }
    }

    /** The color picker's own HSV, kept apart from the color so that hue survives at zero saturation or brightness. */
    private static final class Hsv {
        float hue, sat, val;
        /** The color these values show, or null before the first sync. */
        Integer color;

        void sync(int argb) {
            float[] hsv = Ui.toHsv(argb);
            if (hsv[1] > 0 && hsv[2] > 0) hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
            color = argb;
        }
    }

    private final Albedo sc;
    final Ui ui;
    private final GroupMatches matches = new GroupMatches();
    private final ScriptedInput script = new ScriptedInput();
    private Config saved;
    private Config draft;
    private Policy preview;
    private Page page = Page.of(Category.BLOCKS);
    private Mode mode = Mode.BROWSE;
    private Toast toast;
    /** Shown under the scope switch when a switch was refused. */
    private String notice;
    private boolean closed;
    /** Width of the label column ("Style", "Color", ...) plus its gap; set each frame from the font. */
    private int labelW;
    /** The profile button, and the bottom of the page that the open profile list must stay above. */
    private Rect profileButton = new Rect(0, 0, 0, 0);
    private int bodyBottom;
    private int picked;
    private int suggestion;
    private final Hsv hsv = new Hsv();

    private final TextField hex = new TextField(9, c -> Character.digit(c, 16) >= 0 || c == '#');
    private final TextField search = new TextField(64, TextField.ANY);
    private final TextField add = new TextField(64, c -> Character.isLetterOrDigit(c) || "_-:*?/.^$|()[]{}+\\,".indexOf(c) >= 0);
    private final TextField groupName = new TextField(32, TextField.ANY);
    private final TextField rename = new TextField(32, c -> Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ');
    private final TextField profileSearch = new TextField(32, TextField.ANY);

    public SettingsEditor(final Albedo sc) {
        this.sc = sc;
        this.ui = new Ui(() -> sc.platform().clipboard());
        this.saved = sc.config();
        setDraft(saved);
    }

    // ---------------------------------------------------------------- input from adapters (the pointer position
    // is taken from each render call)

    public void mousePressed(int button) {
        if (button == 0) ui.mousePressed();
    }

    public void mouseReleased(int button) {
        if (button == 0) ui.mouseReleased();
    }

    public void mouseScrolled(double amount) { ui.scrolled(amount); }

    public void keyPressed(UiKey key, boolean ctrl) { ui.key(key, ctrl); }

    public void charTyped(char c) { ui.typed(c); }

    /** The adapter closes its screen once this turns true. */
    public boolean isClosed() { return closed; }

    /** Queues an input for the open screen, from the dev harness (see {@link ScriptedInput}). */
    public static void script(String command) {
        ScriptedInput.add(command);
    }

    // ---------------------------------------------------------------- frame

    public void render(Canvas canvas, int mouseX, int mouseY) {
        boolean scripted = script.active();
        if (scripted) canvas = script.beginFrame(canvas, ui);
        ui.begin(canvas, script.x(mouseX), script.y(mouseY));
        if (ui.clicked()) toast = null;

        int w = canvas.width(), h = canvas.height();
        labelW = ui.width(Labels.members(page.category));
        for (String l : ROW_LABELS) labelW = Math.max(labelW, ui.width(l));
        labelW += 8;
        int pw = Math.min(w - 2 * (w < 440 ? 4 : 10), MAX_W), ph = Math.min(h - 2 * (h < 260 ? 4 : 10), MAX_H);
        int x0 = (w - pw) / 2, y0 = (h - ph) / 2, y1 = y0 + ph;
        ui.fill(0, 0, w, h, Ui.SCRIM);
        ui.round(x0, y0, pw, ph, Ui.BG);

        int bodyTop = y0 + 26;
        bodyBottom = y1 - 24;
        header(x0 + 8, y0 + 6, pw - 16);
        ui.fill(x0 + 8, bodyTop - 3, pw - 16, 1, Ui.BORDER);
        int widest = 0;
        for (Category c : CATEGORIES) widest = Math.max(widest, ui.width(Labels.name(c)));
        int sideW = Math.min(widest + 22, pw / 4 + 20);
        sidebar(x0 + 8, bodyTop + 2, sideW, bodyBottom - bodyTop - 4);
        int cx = x0 + 16 + sideW, cw = pw - 24 - sideW;
        content(cx, bodyTop + 2, cw, bodyBottom - bodyTop - 4);
        ui.fill(x0 + 8, bodyBottom + 1, pw - 16, 1, Ui.BORDER);
        footer(x0 + 8, bodyBottom + 5, pw - 16);
        toast(cx, bodyBottom - 3, cw);
        if (mode == Mode.PICK_PROFILE) profileList();
        escape();

        ui.end();
        if (scripted) script.endFrame(ui);
    }

    /** Takes an Esc that no text field or header mode took this frame, for an armed button, the open group, then the screen. */
    private void escape() {
        if (!ui.takeKey(UiKey.ESCAPE) || ui.disarm()) return;
        if (page.inGroup()) go(page.withGroup(-1));
        else done();
    }

    /** The only way the mode changes: the old mode gives back its focus and overlay, the new one sets its own. */
    private void setMode(Mode next) {
        if (mode == Mode.PICK_PROFILE) {
            ui.releaseFocus("profiles.search"); // a click on Rename has just focused its own field
            ui.endOverlay();
        }
        if (mode == Mode.RENAME) ui.releaseFocus("rename");
        mode = next;
        if (next == Mode.PICK_PROFILE) {
            profileSearch.set("");
            picked = Math.max(0, new ArrayList<String>(draft.profiles().keySet()).indexOf(draft.activeProfileName()));
            ui.focus("profiles.search");
        } else if (next == Mode.RENAME) {
            rename.set(draft.activeProfileName());
            rename.selectAll = true;
            ui.focus("rename");
        }
    }

    /** The only way the page changes: what belongs to the old page (input, suggestion, notice, search) is reset. */
    private void go(Page next) {
        Page prev = page;
        page = next;
        if (next.group != prev.group || next.world != prev.world || next.category != prev.category) {
            add.set("");
            suggestion = 0;
            ui.clearFocus();
        }
        boolean otherCategory = next.category != prev.category;
        if (otherCategory || next.world != prev.world || next.tab != prev.tab) {
            search.set("");
            ui.resetScroll("list");
            notice = null;
        }
        if (otherCategory) ui.resetScroll("groups");
    }

    // ---------------------------------------------------------------- header: title, on/off, profiles

    private void header(int x, int y, int w) {
        if (mode == Mode.RENAME && ui.isFocused("rename") && ui.takeKey(UiKey.ESCAPE)) setMode(Mode.BROWSE);
        int bw = ui.buttonWidth("Copy", "Rename", "Delete");
        int right = x + w, buttonsW = (mode == Mode.RENAME ? 2 : 3) * (bw + 3) - 3;

        // A narrow window drops the title first, so that the profile button keeps its room.
        int stateW = 22 + ui.width("Off"), titleW = ui.width(Albedo.NAME) + 8;
        boolean showTitle = w - 3 * (bw + 3) - 5 - titleW - stateW - 8 >= 70;
        int tx = x;
        if (showTitle) {
            ui.text(Albedo.NAME, x, y + 3, Ui.TEXT);
            tx += titleW;
        }
        boolean on = ui.toggle("enabled", tx, y + 2, draft.enabled(),
                (showTitle ? "" : Albedo.NAME + ". ") + "Turn the mod on or off (" + draft.keys().toggle + ")");
        if (on != draft.enabled()) setDraft(draft.withEnabled(on));
        ui.text(on ? "On" : "Off", tx + 22, y + 3, on ? Ui.TEXT : Ui.MUTED);

        int px = tx + stateW + 8, pw = right - buttonsW - 8 - px;
        if (mode == Mode.RENAME) renameBar(px, y, pw, right, bw);
        else profileBar(px, y, pw, right, bw);
    }

    private void renameBar(int px, int y, int pw, int right, int bw) {
        boolean save = ui.button("rename.save", right - 2 * bw - 3, y, bw, Ui.H, "Save", Ui.Style.PRIMARY, true, "Save the name (Enter)");
        if (ui.button("rename.cancel", right - bw, y, bw, Ui.H, "Cancel", Ui.Style.NORMAL, true, "Keep the old name (Esc)")) {
            setMode(Mode.BROWSE);
            return;
        }
        if (pw >= 40) ui.textField("rename", rename, px, y, pw, Ui.H, "Profile name", "Letters, digits, - and _");
        // Enter or a click elsewhere ends the input; both keep the name.
        if (save || !ui.isFocused("rename")) finishRename();
    }

    private void finishRename() {
        setMode(Mode.BROWSE);
        setDraft(draft.withRenamedProfile(draft.activeProfileName(), rename.text));
    }

    private void profileBar(int px, int y, int pw, int right, int bw) {
        boolean builtIn = Config.isBuiltIn(draft.activeProfileName());
        if (builtIn) {
            ui.button("delete", right - bw, y, bw, Ui.H, "Delete", Ui.Style.DANGER, false, "Built-in profiles cannot be deleted");
        } else if (ui.confirmButton("delete", right - bw, y, bw, Ui.H, "Delete", "Delete this profile",
                "Click again to delete profile \"" + draft.activeProfileName() + "\"")) {
            String gone = draft.activeProfileName();
            switchProfile(draft.withoutProfile(gone));
            say("Deleted profile " + gone + ".");
        }
        if (ui.button("rename", right - 2 * bw - 3, y, bw, Ui.H, "Rename", Ui.Style.NORMAL, !builtIn,
                builtIn ? "Built-in profiles cannot be renamed. Copy it to make your own." : "Rename this profile")) {
            setMode(Mode.RENAME);
        }
        if (ui.button("new", right - 3 * bw - 6, y, bw, Ui.H, "Copy", Ui.Style.NORMAL, true,
                "Make a new profile as a copy of this one")) {
            String name = draft.freeProfileName(draft.activeProfileName() + "-copy");
            setDraft(draft.withProfile(draft.activeProfile().renamed(name)).withActiveProfile(name));
            setMode(Mode.RENAME);
        }
        if (mode == Mode.RENAME || pw < 40) return;

        profileButton = new Rect(px, y, pw, Ui.H);
        if (ui.button("profiles", px, y, pw, Ui.H, "", Ui.Style.NORMAL, true,
                "Select a profile. " + draft.keys().cycleProfile + " selects the next profile in game.")) {
            setMode(mode == Mode.PICK_PROFILE ? Mode.BROWSE : Mode.PICK_PROFILE);
        }
        String name = draft.activeProfileName(), tag = builtIn ? BUILT_IN : "";
        int tagW = tag.isEmpty() ? 0 : ui.width(tag) + 6, room = pw - 18 - tagW;
        String full = "Profile: " + name;
        ui.text(ui.width(full) <= room ? full : ui.ellipsize(name, room), px + 5, y + 3, Ui.TEXT);
        if (tagW > 0) ui.text(tag, px + pw - 12 - tagW, y + 3, Ui.FAINT);
        ui.text("v", px + pw - 10, y + 3, Ui.MUTED);
        // Reserved before the page is drawn, so that a click on the open list does not also reach the page.
        if (mode == Mode.PICK_PROFILE) ui.reserveOverlay(profileListArea());
    }

    // ---------------------------------------------------------------- profile list: a searchable dropdown

    private List<String> profileNames() {
        String q = profileSearch.text.trim().toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<String>();
        for (String n : draft.profiles().keySet()) if (n.contains(q)) names.add(n);
        return names;
    }

    private int profileRows() {
        int top = profileButton.bottom() + 1;
        return Math.max(1, Math.min(profileNames().size(), (bodyBottom - top - Ui.H - 6) / ROW));
    }

    private Rect profileListArea() {
        return new Rect(profileButton.x, profileButton.bottom() + 1, profileButton.w, Ui.H + 6 + profileRows() * ROW);
    }

    /** The profile list over the page, where Up, Down and Enter choose and Esc or a click outside closes it. */
    private void profileList() {
        if (ui.takeKey(UiKey.ESCAPE)) {
            setMode(Mode.BROWSE);
            return;
        }
        Rect area = profileListArea();
        if (ui.clicked() && !ui.pointerIn(area.x, profileButton.y, area.w, area.bottom() - profileButton.y)) {
            setMode(Mode.BROWSE);
            return;
        }
        boolean down = ui.takeKey(UiKey.DOWN), up = ui.takeKey(UiKey.UP), enter = ui.takeKey(UiKey.ENTER);
        int rows = profileRows(), x = area.x, y = area.y, w = area.w;
        ui.beginOverlay();
        ui.panel(area, Ui.BORDER, Ui.PANEL);
        if (ui.textField("profiles.search", profileSearch, x + 2, y + 2, w - 4, Ui.H, "Search profiles", null)) picked = 0;
        ui.focus("profiles.search"); // the list keeps the keyboard while it is open
        List<String> names = profileNames(); // after the field, so that this frame's typing filters the list
        if (down) picked++;
        if (up) picked--;
        picked = Math.max(0, Math.min(picked, names.size() - 1));
        int ly = y + Ui.H + 4;
        if (names.isEmpty()) ui.textFit("No profile has this name", x + 5, ly + 1, w - 10, Ui.FAINT);
        int first = names.size() > rows ? Math.max(0, Math.min(picked - rows + 1, names.size() - rows)) : 0;
        String chosen = null;
        for (int i = first; i < Math.min(names.size(), first + rows); i++) {
            String n = names.get(i);
            int ry = ly + (i - first) * ROW;
            if (i == picked) ui.fill(x + 1, ry, w - 2, ROW, Ui.CARD_HOVER);
            if (n.equals(draft.activeProfileName())) ui.fill(x + 1, ry + 1, 2, ROW - 2, Ui.ACCENT);
            boolean clicked = ui.button("profiles.row." + n, x + 1, ry, w - 2, ROW, "", Ui.Style.GHOST, true, null);
            String tag = Config.isBuiltIn(n) ? BUILT_IN : "";
            int tagW = tag.isEmpty() ? 0 : ui.width(tag) + 6;
            ui.text(ui.ellipsize(n, w - 14 - tagW), x + 6, ry + 1, Ui.TEXT);
            if (tagW > 0) ui.text(tag, x + w - 4 - tagW, ry + 1, Ui.FAINT);
            if (clicked || (enter && i == picked)) chosen = n;
        }
        ui.endOverlay();
        if (chosen != null) {
            switchProfile(draft.withActiveProfile(chosen));
            setMode(Mode.BROWSE);
        }
    }

    // ---------------------------------------------------------------- sidebar: categories, then the World page

    private void sidebar(int x, int y, int w, int h) {
        // Rows shrink a little on short windows so the World entry always shows.
        int pitch = Math.max(10, Math.min(13, (h - 6) / (CATEGORIES.length + 1)));
        int ry = y;
        for (Category c : CATEGORIES) {
            Appearance a = profile().get(c).appearance();
            String tip = Labels.description(c) + (a.isOriginal() ? " Now: vanilla." : "");
            boolean active = !page.world && c == page.category;
            if (sidebarRow("cat." + c.key(), x, ry, w, pitch - 1, Labels.name(c), tip, active, !a.isOriginal())) go(Page.of(c));
            lookIcon(x + 6, ry + (pitch - 9) / 2, a);
            ry += pitch;
        }
        ry += 5;
        ui.fill(x + 4, ry - 3, w - 8, 1, Ui.BORDER);
        if (sidebarRow("world", x, ry, w, pitch - 1, "World", "Fullbright, shading, foliage and shadows", page.world, true)) {
            go(page.toWorld());
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

    /** A sidebar entry that returns true when clicked, with room at x + 6 for the icon of the caller. */
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
        if (page.world) {
            worldPage(x, y, w, h);
            return;
        }
        Category category = page.category;
        CategorySettings cs = profile().get(category);
        if (page.inGroup()) {
            // The group may be gone: deleted, or a different profile is active.
            if (page.group < cs.rules().size()) {
                groupPage(x, y, w, y + h, cs, page.group);
                return;
            }
            go(page.withGroup(-1));
        }
        String name = Labels.name(category);
        ui.text(name, x, y, Ui.TEXT);
        int nameW = ui.width(name) + 8;
        ui.textFit(Labels.description(category), x + nameW, y, w - nameW, Ui.MUTED);
        int cy = y + 13;
        if (category.hasGroups()) {
            String[] labels = {"Look", "Groups " + cs.rules().size(), "Textures " + sc.catalog(category).size()};
            int chosen = ui.tabs("tab", x, cy, w, labels, page.tab.ordinal(), new String[]{
                    "The look of all " + name.toLowerCase(Locale.ROOT) + " textures that are not in a group",
                    "Sets of textures that have their own look",
                    "All loaded textures. Click one to put it in a group."});
            if (chosen != page.tab.ordinal()) {
                go(page.withTab(Tab.values()[chosen]));
                return;
            }
            cy += Ui.H + GAP;
        }
        Tab tab = category.hasGroups() ? page.tab : Tab.LOOK;
        switch (tab) {
            case GROUPS: groupsTab(x, cy, w, y + h - cy, cs); break;
            case TEXTURES: texturesTab(x, cy, w, y + h - cy, cs); break;
            default: lookTab(x, cy, w, y + h, cs);
        }
    }

    private void lookTab(int x, int y, int w, int bottom, CategorySettings cs) {
        Category category = page.category;
        boolean tint = category == Category.BLOCKS || category == Category.FLUIDS;
        int below = (cs.onlyGroups() ? 2 * (Ui.LINE + 1) + GAP : 0) + (tint || category == Category.SKY ? 12 : 0);
        int cy = lookEditor(x, y, w, bottom - below, cs, -1);
        if (cs.onlyGroups()) {
            cy += ui.paragraph("Only groups change (see Groups). Groups set to Global use this look.", x, cy, w, Ui.FAINT) + GAP;
        }
        if (tint) {
            boolean remove = ui.toggleRow("tint", x - 4, cy, w + 4, "Remove biome tint", cs.removeTint(),
                    category == Category.BLOCKS ? "Grass and leaves do not get the biome color" : "Water does not get the biome color");
            if (remove != cs.removeTint()) setCategory(cs.withRemoveTint(remove));
        }
        if (category == Category.SKY) ui.paragraph("This also sets the fog to the sky color.", x, cy, w, Ui.FAINT);
    }

    /**
     * Draws style, color and shape controls for the global look or a group, whose first style is "Global" in the only-groups scope.
     *
     * @param index the group, or -1 for the global look
     * @return the next y
     */
    private int lookEditor(int x, int y, int w, int bottom, CategorySettings cs, int index) {
        Category category = page.category;
        Rule rule = index < 0 ? null : cs.rules().get(index);
        boolean global = rule != null && rule.usesGlobalLook();
        Appearance target = rule == null ? cs.appearance() : rule.appearance();
        List<Appearance.Mode> modes = category.supportedModes();
        String[] labels = new String[modes.size()], tips = new String[modes.size()];
        int selected = 0;
        for (int i = 0; i < modes.size(); i++) {
            labels[i] = Labels.mode(modes.get(i));
            tips[i] = Labels.modeTip(modes.get(i));
            if (!global && modes.get(i) == target.mode()) selected = i;
        }
        if (rule != null && cs.onlyGroups()) {
            labels[0] = "Global";
            tips[0] = "Use the " + Labels.name(category) + " look";
        } else if (rule != null) {
            tips[0] = "Do not change these blocks";
        }
        int lx = x + labelW, cw = w - labelW;
        ui.text("Style", x, y + 3, Ui.MUTED);
        int chosen = ui.segmented("mode", lx, y, cw, labels, selected, tips);
        int cy = y + ui.segmentedHeight(labels, cw) + GAP;
        if (chosen != selected) {
            if (rule != null && cs.onlyGroups() && chosen == 0) setRule(cs, index, rule.withGlobalLook());
            else setLook(cs, index, target.withMode(modes.get(chosen)));
            return cy; // the rest of the layout depends on the style; draw it next frame
        }
        if (global) {
            ui.swatch(lx, cy + 2, 9, 9, chipColor(cs.appearance(), 0xFF808080));
            String text = Labels.modeTip(cs.appearance().mode()) + " (the " + Labels.name(category) + " look)";
            ui.text(ui.ellipsize(text, cw - 14), lx + 13, cy + 3, Ui.MUTED);
            return cy + Ui.H + GAP;
        }
        Appearance.Mode m = target.mode();
        boolean usesShape = m != Appearance.Mode.ORIGINAL && m != Appearance.Mode.HIDDEN
                && category != Category.SKY && category != Category.CLOUDS;
        if (m == Appearance.Mode.TEXTURE) cy = imagePicker(x, cy, w, cs, index, target);
        if (m == Appearance.Mode.SOLID || m == Appearance.Mode.TEXTURE) {
            ui.text("Color", x, cy + 3, Ui.MUTED);
            if (m == Appearance.Mode.TEXTURE) ui.tip(x, cy, labelW, Ui.H, "The color to use when the image is not found");
            // The picker gives up height first when the window is short, so the rows below it stay on screen.
            int pickerH = Math.max(24, Math.min(44, bottom - cy - GAP - (usesShape ? Ui.H + GAP : 0)));
            cy = colorPicker(lx, cy, cw, pickerH, cs, index, target);
        }
        if (usesShape) {
            String[] shapes = {"Keep", "Cut-out", "Solid"};
            ui.text("Shape", x, cy + 3, Ui.MUTED);
            int a = ui.segmented("alpha", lx, cy, cw, shapes, target.alpha().ordinal(),
                    new String[]{"Keep transparent parts (glass, leaves)",
                            "Make each part fully solid or fully transparent",
                            "Fill all holes. Leaves and glass become solid cubes."});
            if (a != target.alpha().ordinal()) setLook(cs, index, target.withAlpha(Appearance.Alpha.values()[a]));
            cy += ui.segmentedHeight(shapes, cw) + GAP;
        }
        return cy;
    }

    // ---------------------------------------------------------------- groups

    private void groupsTab(int x, int y, int w, int h, CategorySettings cs) {
        String name = Labels.name(page.category);
        String[] scopes = {"Everything", "Only groups"};
        ui.text("Scope", x, y + 3, Ui.MUTED);
        int scope = ui.segmented("scope", x + labelW, y, w - labelW, scopes, cs.onlyGroups() ? 1 : 0, new String[]{
                "The " + name + " look applies to all. Groups change their own blocks.",
                "Only blocks in groups change. All other blocks stay vanilla."});
        int cy = y + ui.segmentedHeight(scopes, w - labelW) + GAP;
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
            go(page.withGroup(cs.rules().size()));
            ui.focus("add");
            return;
        }
        List<Rule> rules = cs.rules();
        if (rules.size() > 1) ui.textFit("A higher group wins.", x + nw + 6, cy + 3, w - nw - 6, Ui.FAINT);
        cy += Ui.H + GAP;
        if (rules.isEmpty()) {
            ui.paragraph(cs.onlyGroups() ? "There are no groups. Nothing changes."
                    : "There are no groups. All textures use the " + name + " look.", x, cy + 2, w, Ui.FAINT);
            return;
        }
        int rowH = 16, lh = y + h - cy, visible = Math.max(1, lh / rowH);
        int first = ui.scroll("groups", x, cy, w, lh, rules.size(), rowH);
        int listW = rules.size() > visible ? w - 6 : w;
        for (int i = first; i < Math.min(rules.size(), first + visible); i++) {
            if (groupRow(x, cy + (i - first) * rowH, listW, rowH - 2, cs, i)) return;
        }
    }

    /** One row of the group list, which returns true when a click opened or deleted a group. */
    private boolean groupRow(int x, int y, int w, int h, CategorySettings cs, int i) {
        List<Rule> rules = cs.rules();
        Rule r = rules.get(i);
        int bx = x + w - 50;
        ui.round(x, y, w, h, Ui.PANEL);
        if (ui.button("group." + i, x, y, bx - x - 2, h, "", Ui.Style.GHOST, true, "Edit group " + r.displayName())) {
            go(page.withGroup(i));
            return true;
        }
        Appearance a = cs.lookOf(r);
        lookIcon(x + 4, y + 3, a);
        String look = r.usesGlobalLook() ? "global look" : a.isOriginal() ? "vanilla" : Labels.mode(a.mode()).toLowerCase(Locale.ROOT);
        int room = bx - x - 22;
        int nameW = Math.min(ui.width(r.displayName()), room / 2);
        ui.text(ui.ellipsize(r.displayName(), nameW), x + 16, y + 3, Ui.TEXT);
        String members = r.patterns().isEmpty() ? "no blocks" : String.join(", ", r.patterns());
        ui.text(ui.ellipsize(look + ": " + members, room - nameW - 6), x + 22 + nameW, y + 3, Ui.MUTED);
        // A move keeps drawing the old rows for this frame, so the list does not flicker.
        if (ui.button("group.up." + i, bx, y + 1, 15, 12, "^", Ui.Style.GHOST, i > 0, "Move up. A higher group wins.")) {
            setCategory(cs.withRuleMoved(i, -1));
        }
        if (ui.button("group.down." + i, bx + 16, y + 1, 15, 12, "v", Ui.Style.GHOST, i < rules.size() - 1, "Move down")) {
            setCategory(cs.withRuleMoved(i, 1));
        }
        if (ui.confirmButton("group.del." + i, bx + 34, y + 1, 15, 12, "x", "Delete this group",
                "Click again to delete \"" + r.displayName() + "\"")) {
            setCategory(cs.withoutRule(i));
            return true;
        }
        return false;
    }

    private void groupPage(int x, int y, int w, int bottom, CategorySettings cs, int index) {
        Rule rule = cs.rules().get(index);
        if (ui.button("back", x, y - 3, 13, Ui.H, "<", Ui.Style.GHOST, true, "Back to the groups (Esc)")) {
            go(page.withGroup(-1));
            return;
        }
        String crumb = Labels.name(page.category) + " /";
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
            go(page.withGroup(-1));
            return;
        }
        // Keep room for the block input and one line of chips under the look.
        int cy = lookEditor(x, y + Ui.H + 2, w, bottom - Ui.H - 16, cs, index);
        patternsEditor(x, cy, w, bottom, cs, index);
    }

    /** The group's patterns as removable chips, and an input that suggests matching textures while typing. */
    private void patternsEditor(int x, int y, int w, int bottom, CategorySettings cs, int index) {
        Category category = page.category;
        Map<String, Integer> catalog = sc.catalog(category);
        String members = Labels.members(category);
        ui.text(members, x, y + 3, Ui.MUTED);
        int fx = x + labelW, fw = w - labelW;
        // Keys the list reacts to are taken before the field would (Enter would unfocus it); typing goes to the field.
        boolean focused = ui.isFocused("add");
        boolean down = focused && ui.takeKey(UiKey.DOWN), up = focused && ui.takeKey(UiKey.UP);
        boolean complete = focused && ui.takeKey(UiKey.TAB), enter = focused && ui.takeKey(UiKey.ENTER);
        if (ui.textField("add", add, fx, y, fw, Ui.H, "Type a name, for example " + Labels.example(category) + ". * selects all.",
                "Enter adds the selected match. Tab completes. * matches any text. /text/ is a regular expression.")) {
            suggestion = 0;
        }
        focused = ui.isFocused("add");
        // "/fla" is a regular expression still being typed: it means "/fla/".
        String typed = add.text.trim();
        if (typed.startsWith("/") && !Glob.isRegex(typed)) typed += "/";
        String problem = Glob.problem(typed);
        List<GroupMatches.Suggestion> list = focused && problem == null
                ? matches.suggestions(typed, catalog, cs, index) : Collections.<GroupMatches.Suggestion>emptyList();
        if (down) suggestion++;
        if (up) suggestion--;
        suggestion = Math.max(0, Math.min(suggestion, list.size() - 1));
        if (complete && !list.isEmpty()) add.set(list.get(suggestion).pattern);
        if (enter && !typed.isEmpty() && problem == null) {
            addPattern(cs, index, list.isEmpty() ? toPattern(typed) : list.get(suggestion).pattern);
            return;
        }
        int listY = y + Ui.H + 2, rows = Math.min(list.size(), Math.max(0, (bottom - listY - 2) / ROW));
        Rect listArea = new Rect(fx, listY, fw, rows * ROW + 2);
        if (rows > 0) ui.reserveOverlay(listArea);
        if (focused && problem != null) {
            listY += ui.paragraph("Not a valid regular expression: " + problem, fx, listY + 2, fw, Ui.DANGER) + 2;
        } else if (focused && !add.text.trim().isEmpty() && list.isEmpty()) {
            ui.textFit("No texture matches. Enter adds it as you typed it.", fx, listY + 2, fw, Ui.FAINT);
            listY += 12;
        }
        if (chips(fx, listY + 2, fw, bottom, cs, index, catalog)) return;
        if (cs.rules().get(index).patterns().isEmpty() && rows == 0 && !focused) {
            ui.paragraph("No " + members.toLowerCase(Locale.ROOT) + " yet. Type a name above, or * for all.", fx, listY + 4, fw, Ui.FAINT);
        }
        if (rows > 0) suggestionList(listArea, list.subList(0, rows), cs, index);
    }

    /** The group's patterns as chips in wrapped lines, which returns true when a click removed one. */
    private boolean chips(int fx, int y, int fw, int bottom, CategorySettings cs, int index, Map<String, Integer> catalog) {
        Rule rule = cs.rules().get(index);
        int cx = fx, cy = y;
        for (int i = 0; i < rule.patterns().size(); i++) {
            String p = rule.patterns().get(i);
            GroupMatches.Chip m = matches.chip(cs, index, p, catalog);
            boolean dim = m.matched == 0 || m.effective == 0;
            String tip = m.matched == 0 ? "No loaded texture matches " + p
                    : m.effective == 0 ? "A higher group already has all " + m.matched + " of these textures"
                    : m.effective + " texture" + (m.effective == 1 ? "" : "s");
            int cw = ui.chipWidth(p, m.color != null);
            if (cx + cw > fx + fw && cx > fx) {
                cx = fx;
                cy += 14;
            }
            // Keep a line free for "+N more" unless this is the last chip, so none disappear silently.
            boolean last = i == rule.patterns().size() - 1;
            if (cy + 12 > (last ? bottom : bottom - 11)) {
                int more = rule.patterns().size() - i;
                if (cy + Ui.LINE <= bottom) {
                    ui.textFirstFit(fx, cy + 2, fw, Ui.FAINT, "+" + more + " more. Make the window larger to see them.", "+" + more + " more");
                }
                return false;
            }
            if (ui.chip("chip." + i + "." + p, cx, cy, m.color, p, dim, tip)) {
                List<String> next = new ArrayList<String>(rule.patterns());
                next.remove(i);
                setRule(cs, index, rule.withPatterns(next));
                return true;
            }
            cx += cw + 3;
        }
        return false;
    }

    /** The suggestions under the group input, drawn over the chips. */
    private void suggestionList(Rect area, List<GroupMatches.Suggestion> rows, CategorySettings cs, int index) {
        int fx = area.x, fw = area.w;
        ui.beginOverlay();
        ui.panel(area, Ui.BORDER, Ui.PANEL);
        for (int i = 0; i < rows.size(); i++) {
            GroupMatches.Suggestion s = rows.get(i);
            int ry = area.y + 1 + i * ROW;
            if (i == suggestion) ui.fill(fx + 1, ry, fw - 2, ROW, Ui.CARD_HOVER);
            if (ui.button("sugg." + i, fx + 1, ry, fw - 2, ROW, "", Ui.Style.GHOST, true, null)) {
                ui.endOverlay();
                addPattern(cs, index, s.pattern);
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

    private void addPattern(CategorySettings cs, int index, String pattern) {
        add.set("");
        suggestion = 0;
        if (!pattern.isEmpty()) setCategory(cs.withPatternIn(index, pattern));
        ui.focus("add");
    }

    private static String freeGroupName(CategorySettings cs) {
        for (int n = cs.rules().size() + 1; ; n++) {
            boolean taken = false;
            for (Rule r : cs.rules()) if (r.displayName().equals("Group " + n)) taken = true;
            if (!taken) return "Group " + n;
        }
    }

    // ---------------------------------------------------------------- textures

    /** Every texture seen in this category; clicking one opens its group, or starts a group with it. */
    private void texturesTab(int x, int y, int w, int h, CategorySettings cs) {
        Category category = page.category;
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
            go(page.withGroup(cs.rules().indexOf(owner)));
            return;
        }
        // First place, so the texture really gets this group's look even if a broader group also matches it.
        setCategory(cs.withRuleAt(0, cs.newGroup(shortId(id), Collections.singletonList(shortId(id)))));
        go(page.withGroup(0));
    }

    // ---------------------------------------------------------------- world page

    private void worldPage(int x, int y, int w, int h) {
        ui.text("World", x, y, Ui.TEXT);
        int nameW = ui.width("World") + 8;
        ui.textFit("These settings apply to all categories", x + nameW, y, w - nameW, Ui.MUTED);
        int cy = y + 16;
        // Each row shows its explanation when all rows fit with it; otherwise the explanation is the tooltip only.
        int need = 0;
        for (Toggle t : Toggle.values()) need += 18 + ui.paragraphHeight(Labels.explain(t), w - 12) + 4;
        boolean full = need <= y + h - cy;
        for (Toggle t : Toggle.values()) {
            String explain = Labels.explain(t);
            int rh = full ? 18 + ui.paragraphHeight(explain, w - 12) : 16;
            ui.round(x, cy, w, rh, Ui.PANEL);
            boolean on = profile().is(t);
            if (ui.toggleRow(t.key, x + 2, cy + 2, w - 4, Labels.toggle(t), on, explain) != on) setProfile(profile().with(t, !on));
            if (full) ui.paragraph(explain, x + 6, cy + 15, w - 12, Ui.MUTED);
            cy += rh + 4;
        }
    }

    // ---------------------------------------------------------------- color and image pickers

    private int imagePicker(int x, int y, int w, CategorySettings cs, int index, Appearance target) {
        List<String> names = sc.userTextureNames();
        ui.text("Image", x, y + 3, Ui.MUTED);
        int lx = x + labelW, lw = w - labelW;
        if (names.isEmpty()) {
            return y + ui.paragraph("No images. Put PNG files in config/albedo/textures.", lx, y + 3, lw, Ui.FAINT) + 3 + GAP;
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

    /** Draws a saturation and brightness square, hue and opacity bars, a hex input and a palette, and returns the next y. */
    private int colorPicker(int x, int y, int w, int sqH, CategorySettings cs, int index, Appearance target) {
        int color = target.color();
        if (hsv.color == null || hsv.color != color) hsv.sync(color);
        int alpha = Colors.alpha(color);
        int sqW = Math.min(84, w / 3);

        // saturation (x) / brightness (y) square, drawn in 3px cells
        for (int yy = 0; yy < sqH; yy += 3) {
            for (int xx = 0; xx < sqW; xx += 3) {
                int cell = Ui.hsv(hsv.hue, xx / (float) (sqW - 1), 1 - yy / (float) (sqH - 1));
                ui.fill(x + xx, y + yy, Math.min(3, sqW - xx), Math.min(3, sqH - yy), cell);
            }
        }
        int kx = x + Math.round(hsv.sat * (sqW - 1)), ky = y + Math.round((1 - hsv.val) * (sqH - 1));
        ui.outline(kx - 2, ky - 2, 5, 5, hsv.val > 0.5f ? 0xFF000000 : 0xFFFFFFFF);
        ui.tip(x, y, sqW, sqH, "Saturation and brightness");
        float[] sv = ui.dragArea("sv", x, y, sqW, sqH);
        if (sv != null) {
            hsv.sat = sv[0];
            hsv.val = 1 - sv[1];
        }

        // hue bar
        int hx = x + sqW + 4;
        for (int yy = 0; yy < sqH; yy += 2) ui.fill(hx, y + yy, 8, Math.min(2, sqH - yy), Ui.hsv(yy / (float) sqH, 1, 1));
        ui.fill(hx - 1, y + Math.round(hsv.hue * (sqH - 1)), 10, 1, 0xFFFFFFFF);
        ui.tip(hx, y, 8, sqH, "Hue");
        float[] hv = ui.dragArea("hue", hx, y, 8, sqH);
        if (hv != null) hsv.hue = Math.min(hv[1], 0.999f);

        // opacity bar
        int ax = hx + 12;
        ui.checker(ax, y, 8, sqH);
        int rgb = Ui.hsv(hsv.hue, hsv.sat, hsv.val);
        for (int yy = 0; yy < sqH; yy += 2) ui.fill(ax, y + yy, 8, Math.min(2, sqH - yy), Colors.withAlpha(rgb, 255 - yy * 255 / sqH));
        ui.fill(ax - 1, y + Math.round((1 - alpha / 255f) * (sqH - 1)), 10, 1, 0xFFFFFFFF);
        ui.tip(ax, y, 8, sqH, "Opacity " + Math.round(alpha * 100 / 255f) + "%");
        float[] av = ui.dragArea("alpha.bar", ax, y, 8, sqH);
        if (av != null) alpha = Math.round((1 - av[1]) * 255);

        // Dragging only the opacity keeps the exact RGB (the HSV round trip could shift it slightly).
        int next = Colors.withAlpha(av != null && sv == null && hv == null ? color : rgb, alpha);
        if (next != color) {
            hsv.color = next;
            setLook(cs, index, target.withColor(next));
        }

        // preview, hex and palette
        // The large preview is left out when it would make the hex field too narrow to show a whole color.
        int px = ax + 14, pw = Math.max(40, x + w - px);
        int hexW = Math.min(62, pw), previewW = pw - hexW >= 22 ? 22 : 0;
        if (previewW > 0) ui.swatch(px, y, 18, 18, next);
        if (!ui.isFocused("hex")) hex.set(Colors.format(color));
        if (ui.textField("hex", hex, px + previewW, y + 2, hexW, Ui.H, "#RRGGBB", "Type a color: #RGB, #RRGGBB or #AARRGGBB")) {
            try {
                int typed = Colors.parse(hex.text);
                hsv.sync(typed);
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
                hsv.sync(c);
                setLook(cs, index, target.withColor(c));
            }
        }
        return y + sqH + GAP;
    }

    // ---------------------------------------------------------------- footer

    private void footer(int x, int y, int w) {
        boolean dirty = !draft.equals(saved);
        Config.KeyBinds k = draft.keys();
        int bw = Math.max(44, ui.buttonWidth("Discard", "Apply"));
        int lw = ui.buttonWidth("Export", "Import");
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
            ui.textFirstFit(tx, y + 3, room, Ui.WARN, "Changes not saved", "Not saved");
        } else {
            ui.textFirstFit(tx, y + 3, room, Ui.FAINT, k.toggle + " on/off   " + k.cycleProfile + " next profile   " + k.settings + " this screen",
                    k.toggle + " on/off   " + k.cycleProfile + " next");
        }
    }

    /** The last message: a box above the footer for a few seconds, wrapped so all of it shows. */
    private void toast(int x, int bottom, int w) {
        if (toast == null) return;
        if (System.currentTimeMillis() - toast.at > TOAST_MS) {
            toast = null;
            return;
        }
        int tw = Math.min(w, 240), th = ui.paragraphHeight(toast.text, tw - 12) + 8;
        ui.panel(new Rect(x, bottom - th, tw, th), toast.error ? Ui.DANGER : Ui.BORDER, Ui.TOOLTIP);
        ui.paragraph(toast.text, x + 6, bottom - th + 4, tw - 12, toast.error ? Ui.DANGER : Ui.TEXT);
    }

    private void say(String text) { toast = new Toast(text, false); }

    private void sayError(String text) { toast = new Toast(text, true); }

    private void exportProfile() {
        try {
            File file = sc.exportProfile(profile());
            say("Saved to profiles/" + file.getName() + " and copied to the clipboard");
        } catch (IOException e) {
            sc.platform().warn("Cannot export the profile", e);
            sayError("Cannot save the file: " + e.getMessage());
        }
    }

    private void importProfile() {
        String text = sc.platform().clipboard();
        if (text == null || text.trim().isEmpty()) {
            sayError("The clipboard is empty. Copy the text of a profile file, then push Import.");
            return;
        }
        try {
            switchProfile(draft.withImportedProfile(ConfigCodec.decodeProfileFile(text)));
            say("Added profile " + draft.activeProfileName() + ". Push Apply or Done to keep it.");
        } catch (IllegalArgumentException e) {
            sayError("Cannot import: " + e.getMessage());
        }
    }

    private void apply() {
        RefreshResult r = sc.apply(draft);
        saved = draft;
        say(r.outcome == RefreshResult.Outcome.RELOADED ? "Saved. Resources reload because " + r.reason + "." : "Saved");
    }

    private void done() {
        if (mode == Mode.RENAME) finishRename();
        if (!draft.equals(saved)) sc.apply(draft);
        saved = draft;
        closed = true;
    }

    // ---------------------------------------------------------------- draft editing

    private Profile profile() { return draft.activeProfile(); }

    private void setDraft(Config c) {
        draft = c;
        preview = Policy.of(c.activeProfile());
    }

    /** Selects a different profile, which closes a group of the previous one. */
    private void switchProfile(Config c) {
        setDraft(c);
        go(page.withGroup(-1));
    }

    /** A change to a built-in profile goes into a new copy of it (see {@link Config}); the message says so. */
    private void setProfile(Profile p) {
        String before = draft.activeProfileName();
        setDraft(draft.withProfile(p));
        if (!draft.activeProfileName().equals(before)) {
            say(before + " is built in and does not change. Your changes are in the new profile " + draft.activeProfileName() + ".");
        }
    }

    private void setCategory(CategorySettings cs) { setProfile(profile().with(page.category, cs)); }

    /** Sets the global look ({@code index} -1) or a group's own look. */
    private void setLook(CategorySettings cs, int index, Appearance a) {
        if (index < 0) setCategory(cs.withAppearance(a));
        else setRule(cs, index, cs.rules().get(index).withAppearance(a));
    }

    private void setRule(CategorySettings cs, int index, Rule r) {
        setCategory(cs.withRule(index, r));
    }

    /** The real transform on one pixel of the {@code original} average color, so that previews match the game, or null for hidden. */
    private static Integer chipColor(Appearance a, int original) {
        if (a.mode() == Appearance.Mode.HIDDEN) return null;
        PixelBuffer.Heap pixel = new PixelBuffer.Heap(1, 1, new int[]{original | 0xFF000000});
        PixelTransformer.apply(a.withAlpha(Appearance.Alpha.OPAQUE), pixel, null);
        return pixel.getArgb(0, 0);
    }
}
