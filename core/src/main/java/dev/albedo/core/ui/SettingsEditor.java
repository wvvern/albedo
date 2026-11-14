package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.Glob;
import dev.albedo.core.Policy;
import dev.albedo.core.RefreshResult;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.ServerRule;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.sky.SkyPresets;
import dev.albedo.core.texture.PixelTransformer;
import dev.albedo.core.texture.TextureKey;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
    /** The labels left of controls; the label column is as wide as the widest. */
    private static final String[] ROW_LABELS = {"Style", "Color", "Shape", "Scope", "Recolor"};
    private static final String EFFECTS = "effects";
    /** The first effect under the Effects heading, which all categories with effects have. */
    private static final String CHROMA_LABEL = "Chroma";
    private static final String CHROMA_TIP = "Chroma: each kind of texture gets its own rainbow color";
    private static final String CHROMA_MOVING_TIP = ", and the colors go around at the Speed below.";
    /** The Chroma options, which show under Chroma only while it is on, with short labels that fit the second indent. */
    private static final String[] CHROMA_LABELS = {"Speed", "Vivid", "Bright"};
    private static final String[] CHROMA_IDS = {"effects.chromaSpeed", "effects.chromaSaturation", "effects.chromaBrightness"};
    private static final String[] CHROMA_TIPS = {
        "Speed: how fast the colors go around. 0 stops them.",
        "Vivid (saturation): how strong the colors are.",
        "Bright (brightness): how light the colors are."};
    private static final String EDGE_LABEL = "Edge";
    private static final String EDGE_TIP = "Edge: Chroma colors only the line around each block or item, in a ring of hues.";
    /** The effects after Chroma in the order they apply: blocks and fluids have both, items only Outline. */
    private static final String[] EFFECT_LABELS = {"Bevel", "Outline"};
    private static final String[] EFFECT_TIPS = {
        "Bevel: a soft shade inside the edges of each block, so that blocks of one color stay easy to tell apart.",
        "Outline: a line around the shape of each block or item."};
    private static final String[] RECOLOR_LABELS = {"Detail", "Colors", "Light"};
    private static final String[] RECOLOR_TIPS = {
        "Detail: how much of the light and dark pattern stays. 0%: one flat color. 100%: all of it.",
        "Colors: how much of the original color differences stays. Strong specks, such as ores, always keep their color.",
        "Light: 0% gives the lightness of the color. 100% keeps the lightness of each texture, so dark blocks stay dark."};
    /** The height of the three Recolor sliders on their own rows, with the gap under them. */
    private static final int RECOLOR_H = Form.slidersHeight(RECOLOR_LABELS.length, false);
    /** The height of the Remove biome tint switch. */
    private static final int TINT_ROW_H = 12;
    private static final String BUILT_IN = "built-in";
    /** The choice in the profile list of a server that turns Albedo off there, which no profile name can be (it has a space). */
    private static final String OFF = "Albedo off";
    private static final int ROW = 11;
    /** The distance between the panel rows of the group and server lists. */
    private static final int LIST_ROW = 16;
    private static final int GAP = 6;
    /** The height of a closed section heading. */
    private static final int SECTION_H = Ui.H + GAP;
    private static final int MAX_W = 620, MAX_H = 380;
    private static final long TOAST_MS = 6000;

    private enum Tab { LOOK, GROUPS, TEXTURES }

    /** The header's mode, which only {@link #setMode} changes, so that each mode gives back the focus it took. */
    private enum Mode {
        BROWSE,
        /** The searchable profile list is open under the profile button, or under the profile of a server ({@link #pickServer}). */
        PICK_PROFILE,
        /** The profile name is a text field, with Save and Cancel. */
        RENAME
    }

    private enum View { CATEGORY, WORLD, SERVERS }

    /** What the content area shows (a category tab, a group, the World or the Servers page), which only {@link #go} changes. */
    private static final class Page {
        final View view;
        final Category category;
        final Tab tab;
        /** The open group, or -1. */
        final int group;

        private Page(View view, Category category, Tab tab, int group) {
            this.view = view;
            this.category = category;
            this.tab = tab;
            this.group = group;
        }

        static Page of(Category c) { return new Page(View.CATEGORY, c, Tab.LOOK, -1); }

        Page to(View v) { return new Page(v, category, tab, -1); }

        Page withTab(Tab t) { return new Page(View.CATEGORY, category, t, -1); }

        /** Opens a group of the category, or with -1 goes back to the tab it was opened from. */
        Page withGroup(int index) { return new Page(index < 0 ? view : View.CATEGORY, category, tab, index); }

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

    private final Albedo sc;
    final Ui ui;
    private final GroupMatches matches = new GroupMatches();
    private final ScriptedInput script = new ScriptedInput();
    private final Frame frame = new Frame();
    /** Set when this frame changed the draft, the page or the mode. */
    private boolean changed;
    private Config saved;
    private Config draft;
    private Policy preview;
    private Page page = Page.of(Category.BLOCKS);
    private Mode mode = Mode.BROWSE;
    private Toast toast;
    /** True while the Effects section is open, for all categories and groups; only a click on its heading changes it. */
    private boolean effectsOpen;
    /** Shown under the scope switch when a switch was refused. */
    private String notice;
    private boolean closed;
    /** Width of the label column ("Style", "Color", ...) plus its gap; set each frame from the font. */
    private int labelW;
    /** The button that the open profile list belongs to, and the top and bottom of the page that the list must stay inside. */
    private Rect pickAnchor = new Rect(0, 0, 0, 0);
    private int bodyTop, bodyBottom;
    /** The server whose profile the open list picks, or null when it picks the profile of the screen. */
    private String pickServer;
    /** Whether the button of {@link #pickServer} showed in this frame, so that the list closes when its row scrolls away. */
    private boolean pickAnchorShown;
    private int picked;
    private int suggestion;
    private final ColorPicker picker;
    private final SkyLayersEditor skyLayers;

    private final TextField search = new TextField(64, TextField.ANY);
    private final TextField add = new TextField(64, c -> Character.isLetterOrDigit(c) || "_-:*?/.^$|()[]{}+\\,".indexOf(c) >= 0);
    private final TextField groupName = new TextField(32, TextField.ANY);
    private final TextField rename = new TextField(32, c -> Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ');
    private final TextField profileSearch = new TextField(32, TextField.ANY);
    private final TextField serverName = new TextField(64, c -> Character.isLetterOrDigit(c) || ".-:[]".indexOf(c) >= 0);

    public SettingsEditor(final Albedo sc) {
        this.sc = sc;
        this.ui = new Ui(() -> sc.platform().clipboard());
        this.picker = new ColorPicker(ui);
        this.skyLayers = new SkyLayersEditor(ui, () -> changed = true, e -> sc.platform().warn("Cannot make the sky preview", e));
        this.saved = sc.config();
        setDraft(saved);
    }

    /** The left button went down, which each adapter finds by its game's own button number because the numbers change between versions. */
    public void mousePressed() { ui.mousePressed(); }

    public void mouseReleased() { ui.mouseReleased(); }

    public void mouseScrolled(double amount) { ui.scrolled(amount); }

    public void keyPressed(UiKey key) { ui.key(key); }

    /** A typed character, where the editor drops control characters, so that an adapter can pass each character that its game gives. */
    public void charTyped(char c) { ui.typed(c); }

    /** The adapter closes its screen once this turns true. */
    public boolean isClosed() { return closed; }

    /**
     * The cursor {x0, y0, x1, y1} in GUI pixels of the text field that takes typing after the last render, or null when no
     * field does, for a game that sends text only while it asks the window for text input.
     */
    public int[] textCursor() {
        Rect r = ui.textCursor();
        return r == null ? null : new int[]{r.x, r.y, r.x + r.w, r.bottom()};
    }

    /** Queues an input for the open screen, from the dev harness (see {@link ScriptedInput}). */
    public static void script(String command) {
        ScriptedInput.add(command);
    }

    /**
     * Takes the GUI position {x, y} where a {@code realclick} command found its label, for the harness to click through the
     * game's own mouse handler, or null when no such click waits.
     */
    public static int[] takeRealClick() {
        return ScriptedInput.takeRealClick();
    }

    public void render(Canvas canvas, int mouseX, int mouseY) {
        boolean scripted = script.active();
        if (scripted) canvas = script.beginFrame(canvas, ui);
        int mx = script.x(mouseX), my = script.y(mouseY);
        changed = false;
        draw(frame.begin(canvas), mx, my);
        // Input that changed the layout drew a mix of old and new state, so the frame draws again with the input used up.
        if (changed) draw(frame.begin(canvas), mx, my);
        frame.show();
        if (scripted) script.endFrame(ui);
    }

    private void draw(Canvas canvas, int mouseX, int mouseY) {
        ui.begin(canvas, mouseX, mouseY);
        if (ui.clicked()) toast = null;

        int w = canvas.width(), h = canvas.height();
        labelW = 0; // the same for every category, so that the controls do not move when the category changes
        for (Category c : CATEGORIES) labelW = Math.max(labelW, ui.width(Labels.members(c)));
        for (String l : ROW_LABELS) labelW = Math.max(labelW, ui.width(l));
        for (String l : RECOLOR_LABELS) labelW = Math.max(labelW, ui.width(l));
        labelW = Math.max(labelW, ui.width(CHROMA_LABEL) + Form.INDENT);
        for (String l : EFFECT_LABELS) labelW = Math.max(labelW, ui.width(l) + Form.INDENT);
        for (String l : CHROMA_LABELS) labelW = Math.max(labelW, ui.width(l) + 2 * Form.INDENT);
        labelW += 8;
        int pw = Math.min(w - 2 * (w < 440 ? 4 : 10), MAX_W), ph = Math.min(h - 2 * (h < 260 ? 4 : 10), MAX_H);
        int x0 = (w - pw) / 2, y0 = (h - ph) / 2, y1 = y0 + ph;
        ui.fill(0, 0, w, h, Ui.SCRIM);
        ui.round(x0, y0, pw, ph, Ui.BG);
        ui.tipBounds(new Rect(x0, y0, pw, ph));

        bodyTop = y0 + 26;
        bodyBottom = y1 - 24;
        pickAnchorShown = false;
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
        toast(cx, bodyTop + 2, bodyBottom - 3, cw);
        if (mode == Mode.PICK_PROFILE && pickServer != null && !pickAnchorShown) setMode(Mode.BROWSE);
        if (mode == Mode.PICK_PROFILE) profileList();
        escape();
        ui.end();
    }

    /** Takes an Esc that no text field or header mode took this frame, for an armed button, the open group, then the screen. */
    private void escape() {
        if (!ui.takeKey(UiKey.ESCAPE) || ui.disarm()) return;
        if (page.view == View.CATEGORY && page.category == Category.SKY && skyLayers.back()) return;
        if (page.inGroup()) go(page.withGroup(-1));
        else done();
    }

    /** The only way the mode changes: the old mode gives back its focus and overlay, the new one sets its own. */
    private void setMode(Mode next) {
        if (mode == Mode.PICK_PROFILE) {
            ui.releaseFocus("profiles.search"); // a click on Rename has just focused its own field
            ui.endOverlay();
            pickServer = null;
        }
        if (mode == Mode.RENAME) ui.releaseFocus("rename");
        mode = next;
        changed = true;
        if (next == Mode.PICK_PROFILE) {
            profileSearch.set("");
            picked = Math.max(0, profileNames().indexOf(pickedNow()));
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
        changed = true;
        if (next.group != prev.group || next.view != prev.view || next.category != prev.category) {
            add.set("");
            serverName.set("");
            suggestion = 0;
            ui.clearFocus();
        }
        boolean otherCategory = next.category != prev.category;
        if (otherCategory || next.view != prev.view || next.tab != prev.tab) {
            search.set("");
            ui.resetScroll("list");
            notice = null;
        }
        if (otherCategory) ui.resetScroll("groups");
        if (otherCategory || next.view != prev.view || next.tab != prev.tab || next.group != prev.group) ui.resetScroll("look");
        if (otherCategory || next.view != prev.view) skyLayers.reset();
    }

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
        String enabledTip = "Turn " + Albedo.NAME + " on or off (" + draft.keys().toggle + ")";
        boolean on = ui.toggle("enabled", tx, y + 2, draft.enabled(), enabledTip);
        // A click on the On or Off text flips the switch too.
        if (ui.press("enabled.label", tx + 20, y, stateW - 20, Ui.H)) on = !on;
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
        if (pw >= 40) ui.textField("rename", rename, px, y, pw, Ui.H, "Profile name", "Letters, digits, - and _, where a space becomes -");
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

        boolean listHere = mode == Mode.PICK_PROFILE && pickServer == null;
        Rect button = new Rect(px, y, pw, Ui.H);
        if (listHere) pickAnchor = button;
        if (ui.button("profiles", px, y, pw, Ui.H, "", Ui.Style.NORMAL, true,
                "Select a profile. " + draft.keys().cycleProfile + " selects the next profile in game.")) {
            openPicker(null, button, listHere);
            listHere = mode == Mode.PICK_PROFILE;
        }
        String name = draft.activeProfileName(), tag = builtIn ? BUILT_IN : "";
        int tagW = tag.isEmpty() ? 0 : ui.width(tag) + 6, room = pw - 18 - tagW;
        String full = "Profile: " + name;
        ui.text(ui.width(full) <= room ? full : ui.ellipsize(name, room), px + 5, y + 3, Ui.TEXT);
        if (tagW > 0) ui.text(tag, px + pw - 12 - tagW, y + 3, Ui.FAINT);
        ui.text("v", px + pw - 10, y + 3, Ui.MUTED);
        // Reserved before the page is drawn, so that a click on the open list does not also reach the page.
        if (listHere) ui.reserveOverlay(profileListArea());
    }

    /** Opens the profile list for a server (null: the profile of the screen), or closes it when it is open there. */
    private void openPicker(String server, Rect anchor, boolean openHere) {
        if (openHere) {
            setMode(Mode.BROWSE);
            return;
        }
        if (mode == Mode.PICK_PROFILE) setMode(Mode.BROWSE);
        pickServer = server;
        pickAnchor = anchor;
        setMode(Mode.PICK_PROFILE);
    }

    /** The name the open list marks as chosen: the profile of the screen, or the profile of the server ({@link #OFF} for none). */
    private String pickedNow() {
        if (pickServer == null) return draft.activeProfileName();
        ServerRule r = draft.servers().get(pickServer);
        return r == null || r.profile() == null ? OFF : r.profile();
    }

    /** The names in the open list that match the search, with {@link #OFF} first for a server. */
    private List<String> profileNames() {
        String q = profileSearch.text.trim().toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<String>();
        if (pickServer != null && OFF.toLowerCase(Locale.ROOT).contains(q)) names.add(OFF);
        for (String n : draft.profiles().keySet()) if (n.contains(q)) names.add(n);
        return names;
    }

    /** The open list under its button, or above it when there is more room there. */
    private Rect profileListArea() {
        int need = Math.max(1, pickServer == null ? draft.profiles().size() : draft.profiles().size() + 1);
        int below = (bodyBottom - pickAnchor.bottom() - 1 - Ui.H - 6) / ROW, above = (pickAnchor.y - 1 - bodyTop - Ui.H - 6) / ROW;
        boolean up = below < need && above > below;
        int rows = Math.max(1, Math.min(need, up ? above : below)), h = Ui.H + 6 + rows * ROW;
        // As wide as the longest name with its tag, ending where the button ends.
        int w = ui.width(OFF) + 16;
        for (String n : draft.profiles().keySet()) w = Math.max(w, ui.width(n) + (Config.isBuiltIn(n) ? ui.width(BUILT_IN) + 6 : 0) + 16);
        w = Math.max(pickAnchor.w, Math.min(w, pickAnchor.x + pickAnchor.w - 4));
        return new Rect(pickAnchor.x + pickAnchor.w - w, up ? pickAnchor.y - 1 - h : pickAnchor.bottom() + 1, w, h);
    }

    /** The profile list over the page, where Up, Down and Enter choose and Esc or a click outside closes it. */
    private void profileList() {
        if (ui.takeKey(UiKey.ESCAPE)) {
            setMode(Mode.BROWSE);
            return;
        }
        Rect area = profileListArea();
        int top = Math.min(area.y, pickAnchor.y), bottom = Math.max(area.bottom(), pickAnchor.bottom());
        if (ui.clicked() && !ui.pointerIn(area.x, top, area.w, bottom - top)) {
            setMode(Mode.BROWSE);
            return;
        }
        boolean down = ui.takeKey(UiKey.DOWN), up = ui.takeKey(UiKey.UP), enter = ui.takeKey(UiKey.ENTER);
        int rows = (area.h - Ui.H - 6) / ROW, x = area.x, y = area.y, w = area.w;
        String now = pickedNow();
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
            if (n.equals(now)) ui.fill(x + 1, ry + 1, 2, ROW - 2, Ui.ACCENT);
            boolean clicked = ui.button("profiles.row." + n, x + 1, ry, w - 2, ROW, "", Ui.Style.GHOST, true, null);
            String tag = Config.isBuiltIn(n) ? BUILT_IN : "";
            int tagW = tag.isEmpty() ? 0 : ui.width(tag) + 6;
            ui.text(ui.ellipsize(n, w - 14 - tagW), x + 6, ry + 1, n.equals(OFF) ? Ui.MUTED : Ui.TEXT);
            if (tagW > 0) ui.text(tag, x + w - 4 - tagW, ry + 1, Ui.FAINT);
            if (clicked || (enter && i == picked)) chosen = n;
        }
        ui.endOverlay();
        if (chosen == null) return;
        if (pickServer == null) switchProfile(draft.withActiveProfile(chosen));
        else setDraft(draft.withServer(new ServerRule(pickServer, chosen.equals(OFF) ? null : chosen)));
        setMode(Mode.BROWSE);
    }

    private void sidebar(int x, int y, int w, int h) {
        // Rows shrink a little on short windows so that the World and Servers entries always show.
        int pitch = Math.max(10, Math.min(13, (h - 11) / (CATEGORIES.length + 2)));
        int ry = y;
        for (Category c : CATEGORIES) {
            Appearance a = profile().get(c).appearance();
            String tip = Labels.description(c) + (a.isOriginal() ? " Now: vanilla." : "");
            boolean active = page.view == View.CATEGORY && c == page.category;
            if (sidebarRow("cat." + c.key(), x, ry, w, pitch - 1, Labels.name(c), tip, active, !a.isOriginal())) go(Page.of(c));
            lookIcon(x + 6, ry + (pitch - 9) / 2, a);
            ry += pitch;
        }
        ry += 5;
        ui.fill(x + 4, ry - 3, w - 8, 1, Ui.BORDER);
        if (sidebarRow("world", x, ry, w, pitch - 1, "World", "Fullbright, shading, foliage, shadows and armor", page.view == View.WORLD, true)) {
            go(page.to(View.WORLD));
        }
        int iy = ry + (pitch - 9) / 2;
        ui.fill(x + 9, iy, 2, 8, Ui.MUTED);
        ui.fill(x + 6, iy + 3, 8, 2, Ui.MUTED);
        ui.fill(x + 8, iy + 2, 4, 4, Ui.TEXT);
        ry += pitch;
        if (sidebarRow("servers", x, ry, w, pitch - 1, "Servers", "The profile for each multiplayer server, for all profiles",
                page.view == View.SERVERS, true)) {
            go(page.to(View.SERVERS));
        }
        iy = ry + (pitch - 9) / 2;
        ui.outline(x + 6, iy, 8, 4, Ui.MUTED);
        ui.outline(x + 6, iy + 4, 8, 4, Ui.MUTED);
    }

    /** An 8x8 icon for a look: its color, or an empty outline for vanilla. */
    private void lookIcon(int x, int y, Appearance a) {
        if (a.isOriginal()) ui.outline(x, y, 8, 8, Ui.FAINT);
        else ui.swatch(x, y, 8, 8, chipColor(a, 0xFF808080, null));
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

    private void content(int x, int y, int w, int h) {
        if (page.view == View.WORLD) {
            worldPage(x, y, w, h);
            return;
        }
        if (page.view == View.SERVERS) {
            serversPage(x, y, w, h);
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
        int cy = y + ui.title(x, y, w, name, Labels.description(category));
        Tab tab = page.tab;
        // A category without groups shows only the Look tab, so that the Style row has the same place on every page.
        String lookTip = category.hasGroups() ? "The look of all " + name.toLowerCase(Locale.ROOT) + " textures that are not in a group"
                : "The look of the " + name.toLowerCase(Locale.ROOT);
        String[] labels = category.hasGroups()
                ? new String[]{"Look", "Groups " + cs.rules().size(), "Textures " + sc.catalog(category).size()} : new String[]{"Look"};
        String[] tips = category.hasGroups()
                ? new String[]{lookTip, "Sets of textures that have their own look", "All loaded textures. Click one to put it in a group."}
                : new String[]{lookTip};
        int chosen = ui.tabs("tab", x, cy, w, labels, tab.ordinal(), tips);
        if (chosen != tab.ordinal()) {
            go(page.withTab(Tab.values()[chosen]));
            return;
        }
        cy += Ui.H + GAP;
        switch (tab) {
            case GROUPS: groupsTab(x, cy, w, y + h - cy, cs); break;
            case TEXTURES: texturesTab(x, cy, w, y + h - cy, cs); break;
            default: lookTab(x, cy, w, y + h, cs);
        }
    }

    private void lookTab(int x, int y, int w, int bottom, CategorySettings cs) {
        Category category = page.category;
        if (category == Category.SKY && cs.appearance().mode() == Appearance.Mode.LAYERS) {
            // The layer editor scrolls its own list, so the look rows above it do not scroll.
            int cy = Column.stack(x, y, w, lookItems(cs, -1, w, bottom - y, 0));
            CategorySettings now = profile().get(category);
            if (now.appearance().mode() == Appearance.Mode.LAYERS) {
                skyLayers.draw(x, cy, w, bottom, ui.c.height(), now.sky(), d -> setCategory(profile().get(Category.SKY).withSky(d)));
            }
            return;
        }
        List<Column.Item> after = lookTabItems(cs);
        int afterH = 0;
        for (Column.Item item : after) afterH += item.height(w);
        List<Column.Item> items = lookItems(cs, -1, w, bottom - y, afterH);
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
    private List<Column.Item> lookTabItems(CategorySettings cs) {
        Category category = page.category;
        List<Column.Item> items = new ArrayList<Column.Item>();
        if (cs.onlyGroups()) items.add(note("Only groups change (see Groups). Groups set to Global use this look."));
        if (category == Category.BLOCKS || category == Category.FLUIDS) {
            items.add(Column.of(TINT_ROW_H, (x, y, w) -> {
                boolean remove = ui.toggleRow("tint", x - 4, y, w + 4, "Remove biome tint", cs.removeTint(),
                        category == Category.BLOCKS ? "Grass and leaves do not get the biome color" : "Water does not get the biome color");
                if (remove == cs.removeTint()) return false;
                setCategory(cs.withRemoveTint(remove));
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
        return Column.of(w -> ui.paragraphHeight(text, w) + GAP, (x, y, w) -> {
            ui.paragraph(text, x, y, w, Ui.FAINT);
            return false;
        });
    }

    private Form form(int x, int w) {
        return new Form(ui, x, w, labelW);
    }

    /**
     * The rows of the global look or a group's look: style, color, recolor, shape and the Effects section; the first
     * style of a group is "Global" in the only-groups scope.
     *
     * @param index the group, or -1 for the global look
     * @param w the width of the column, which the picker size is estimated for
     * @param viewH the height of the column, which the closed rows fit in when they can
     * @param after the height of the rows that the page puts under these
     */
    private List<Column.Item> lookItems(CategorySettings cs, int index, int w, int viewH, int after) {
        Category category = page.category;
        Rule rule = index < 0 ? null : cs.rules().get(index);
        boolean global = rule != null && rule.usesGlobalLook();
        Appearance target = cs.lookOf(rule);
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
        List<Column.Item> items = new ArrayList<Column.Item>();
        items.add(Column.of(iw -> ui.segmentedHeight(labels, iw - labelW) + GAP, (x, y, iw) -> {
            Form form = form(x, iw);
            form.label(y, Ui.H, "Style", null);
            int chosen = ui.segmented("mode", form.lx(), y, form.cw(), labels, selected, tips);
            if (chosen == selected) return false;
            if (rule != null && cs.onlyGroups() && chosen == 0) setRule(cs, index, rule.withGlobalLook());
            else if (modes.get(chosen) == Appearance.Mode.LAYERS && cs.sky().isEmpty()) {
                // A new custom sky starts from a preset, so that the first look is a sky and not black.
                setCategory(cs.withAppearance(target.withMode(Appearance.Mode.LAYERS)).withSky(SkyPresets.DUSK));
            } else setLook(cs, index, target.withMode(modes.get(chosen)));
            // A hidden page of the layer editor must not take the next Esc.
            if (category == Category.SKY) skyLayers.reset();
            return true; // the rest depends on the style
        }));
        if (global) {
            items.add(Column.of(Ui.H + GAP, (x, y, iw) -> {
                Form form = form(x, iw);
                ui.swatch(form.lx(), y + 2, 9, 9, chipColor(cs.appearance(), 0xFF808080, null));
                String text = Labels.modeTip(cs.appearance().mode()) + " (the " + Labels.name(category) + " look)";
                ui.text(ui.ellipsize(text, form.cw() - 14), form.lx() + 13, y + 3, Ui.MUTED);
                return false;
            }));
            return items;
        }
        Appearance.Mode m = target.mode();
        boolean usesShape = target.changesPixels() && category != Category.SKY && category != Category.CLOUDS;
        boolean recolor = m == Appearance.Mode.RECOLOR;
        boolean effects = target.changesPixels() && category.hasChroma();
        String shapeNote = usesShape && category == Category.PLAYERS
                ? "Skins always keep their shape. Shape changes only capes and elytra." : null;
        Appearance.Alpha[] shapes = Appearance.Alpha.values();

        // The picker gives up height first when the window is short, so the closed rows below it stay in view.
        int fixed = ui.segmentedHeight(labels, w - labelW) + GAP + after + (effects ? SECTION_H : 0)
                + (usesShape ? ui.choiceHeight(shapes, Labels::alpha, w - labelW) + GAP : 0);
        int room = viewH - fixed - GAP - (recolor ? RECOLOR_H : 0), sqH = Math.min(44, room);
        boolean full = room >= ColorPicker.MIN_SQUARE;
        int colorH = full ? ColorPicker.height(sqH) : ColorPicker.compactHeight();
        // A very short window puts the recolor sliders on one row and names them in their tooltips.
        boolean oneRow = viewH - fixed - colorH < RECOLOR_H;
        // The shape note shows only when the closed rows keep their room, as the Effects heading comes first.
        int left = viewH - fixed - colorH - (recolor ? Form.slidersHeight(RECOLOR_LABELS.length, oneRow) : 0);
        boolean withNote = shapeNote != null && left >= ui.paragraphHeight(shapeNote, w) + GAP;
        if (m == Appearance.Mode.SOLID || recolor) {
            items.add(Column.of(colorH, (x, y, iw) -> {
                Form form = form(x, iw);
                form.label(y, Ui.H, "Color", recolor ? "The average color of the result" : null);
                int color = target.color();
                int next = full ? picker.draw("look", form.lx(), y, form.cw(), sqH, color)
                        : picker.compact("look", form.lx(), y, form.cw(), color);
                if (next != color) setLook(cs, index, target.withColor(next));
                return false;
            }));
        }
        if (recolor) {
            items.add(Column.of(Form.slidersHeight(RECOLOR_LABELS.length, oneRow), (x, y, iw) -> {
                int[] v = {target.detail(), target.keepColors(), target.keepLight()};
                form(x, iw).sliders("recolor", "Recolor", RECOLOR_LABELS, RECOLOR_TIPS, v, y, oneRow);
                Appearance next = target.withRecolor(v[0], v[1], v[2]);
                if (!next.equals(target)) setLook(cs, index, next);
                return false;
            }));
        }
        if (usesShape) {
            items.add(Column.of(iw -> ui.choiceHeight(shapes, Labels::alpha, iw - labelW) + GAP, (x, y, iw) -> {
                Form form = form(x, iw);
                form.label(y, Ui.H, "Shape", null);
                Appearance.Alpha a = ui.choice("alpha", form.lx(), y, form.cw(), shapes, target.alpha(), Labels::alpha, Labels::alphaTip);
                if (a != target.alpha()) setLook(cs, index, target.withAlpha(a));
                return false;
            }));
            if (withNote) items.add(note(shapeNote));
        }
        if (effects) items.addAll(effectsSection(cs, index, target));
        return items;
    }

    /**
     * The Effects heading, and under it when it is open Chroma with its options while it is on (Speed only where the
     * GPU pass moves the colors, Edge only with Outline), then Bevel and Outline for blocks and fluids, and Outline for
     * items.
     */
    private List<Column.Item> effectsSection(CategorySettings cs, int index, Appearance target) {
        boolean edges = page.category.hasOutline(), bevel = page.category.hasBevel(), open = effectsOpen;
        Chroma chroma = target.chroma();
        boolean more = chroma.isOn() || edges, speed = sc.chromaCanMove(page.category);
        // The Chroma options that show, as indexes into CHROMA_LABELS.
        int[] shown = speed ? new int[]{0, 1, 2} : new int[]{1, 2};
        int optionRows = shown.length + (edges ? 1 : 0);
        // The first of EFFECT_LABELS that shows: Bevel needs square tiles, so items show only Outline.
        int first = bevel ? 0 : 1;
        String[] edgeLabels = Arrays.copyOfRange(EFFECT_LABELS, first, EFFECT_LABELS.length);
        // One item for each group of rows, so that a short window scrolls to each, with one guide line through all of them.
        int chromaH = more ? Form.rowsHeight(1) : Form.slidersHeight(1, false);
        int optionsH = edges ? Form.rowsHeight(optionRows) : Form.slidersHeight(optionRows, false);
        int edgesH = Form.slidersHeight(edgeLabels.length, false);
        List<Column.Item> items = new ArrayList<Column.Item>();
        items.add(Column.of(open ? Ui.H + 2 : SECTION_H, (x, y, w) -> {
            String tip = (open ? "Hide" : "Show") + " the effects: " + CHROMA_LABEL + (edges ? ", " + String.join(", ", edgeLabels) : "");
            if (ui.sectionHeader(EFFECTS, x - 4, y, w + 4, "Effects", Labels.effects(target, bevel, edges), open, tip) == open) return false;
            effectsOpen = !open;
            changed = true;
            return true; // the rows under the heading move
        }));
        if (!open) return items;
        items.add(Column.of(chromaH, (x, y, w) -> {
            Form form = form(x, w);
            form.guide(y, more ? chromaH : chromaH - GAP);
            String tip = CHROMA_TIP + (speed ? CHROMA_MOVING_TIP : ".");
            int strength = form.indented().slider("effects.chroma", y, CHROMA_LABEL, tip, chroma.strength());
            if (strength == chroma.strength()) return false;
            setLook(cs, index, target.withEffects(target.bevel(), target.outline(), chroma.withStrength(strength)));
            return (strength > 0) != chroma.isOn(); // the options show or hide under it
        }));
        if (chroma.isOn()) {
            // The options under Chroma, one indent further right with a guide line of their own.
            items.add(Column.of(optionsH, (x, y, w) -> {
                Form form = form(x, w);
                form.guide(y, edges ? optionsH : optionsH - GAP);
                Form rows = form.indented();
                rows.guide(y, Form.rowsHeight(optionRows) - 3);
                int[] o = {chroma.speed(), chroma.saturation(), chroma.brightness()};
                for (int row = 0; row < shown.length; row++) {
                    int i = shown[row];
                    String text = i == 0 && o[i] == 0 ? "Still" : o[i] + "%";
                    o[i] = rows.indented().slider(CHROMA_IDS[i], y + Form.rowsHeight(row), CHROMA_LABELS[i], CHROMA_TIPS[i], o[i], text);
                }
                boolean edge = edges
                        ? rows.indented().toggle("effects.chromaEdge", y + Form.rowsHeight(shown.length), EDGE_LABEL, EDGE_TIP, chroma.edge())
                        : chroma.edge();
                Chroma next = chroma.withOptions(o[0], o[1], o[2]).withEdge(edge);
                if (!next.equals(chroma)) setLook(cs, index, target.withEffects(target.bevel(), target.outline(), next));
                return false;
            }));
        }
        if (edges) {
            items.add(Column.of(edgesH, (x, y, w) -> {
                Form form = form(x, w);
                form.guide(y, edgesH - GAP);
                int[] e = {target.bevel(), target.outline()}, part = Arrays.copyOfRange(e, first, e.length);
                form.indented().sliders(EFFECTS, "Effects", edgeLabels, Arrays.copyOfRange(EFFECT_TIPS, first, EFFECT_TIPS.length), part, y, false);
                System.arraycopy(part, 0, e, first, part.length);
                if (e[0] != target.bevel() || e[1] != target.outline()) setLook(cs, index, target.withEffects(e[0], e[1], chroma));
                return false;
            }));
        }
        if (speed && chroma.moves() && sc.chromaStill()) {
            items.add(note("The colors stay still, because the GPU cannot move them here."));
        }
        return items;
    }

    private void groupsTab(int x, int y, int w, int h, CategorySettings cs) {
        String name = Labels.name(page.category);
        String[] scopes = {"Everything", "Only groups"};
        Form form = form(x, w);
        form.label(y, Ui.H, "Scope", null);
        int scope = ui.segmented("scope", form.lx(), y, form.cw(), scopes, cs.onlyGroups() ? 1 : 0, new String[]{
                "The " + name + " look applies to all. Groups change their own textures.",
                "Only textures in groups change. All other textures stay vanilla."});
        int cy = y + ui.segmentedHeight(scopes, form.cw()) + GAP;
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
        if (ui.button("group.new", x, cy, nw, Ui.H, newLabel, Ui.Style.PRIMARY, true, "Make a set of textures with its own look")) {
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
        RowList.draw(ui, "groups", x, cy, w, y + h - cy, rules.size(), LIST_ROW, (i, rx, ry, rw, rh) -> groupRow(rx, ry, rw, rh, cs, i));
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
        String members = r.patterns().isEmpty() ? "no textures" : String.join(", ", r.patterns());
        ui.text(ui.ellipsize(look + ": " + members, room - nameW - 6), x + 22 + nameW, y + 3, Ui.MUTED);
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
        if (ui.backButton("back", x, y - 3, "Back to the groups (Esc)")) {
            go(page.withGroup(-1));
            return;
        }
        String crumb = Labels.name(page.category) + " /";
        ui.text(crumb, x + 16, y, Ui.MUTED);
        int dw = ui.buttonWidth("Delete");
        int nx = x + 20 + ui.width(crumb), nw = Math.min(140, x + w - dw - 6 - nx);
        if (!ui.isFocused("group.name")) groupName.set(rule.displayName());
        if (ui.textField("group.name", groupName, nx, y - 3, nw, Ui.H, "Group name", "The name of this group")
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
        // The look scrolls above the room for the block input and one line of chips, which the input's suggestions cover.
        int top = y + Ui.H + 2, viewH = bottom - Ui.H - 16 - top;
        int cy = Column.draw(ui, "look", new Rect(x, top, w, viewH), lookItems(cs, index, w, viewH, 0));
        patternsEditor(x, cy, w, bottom, cs, index);
    }

    /** The group's patterns as removable chips, and an input that suggests matching textures while typing. */
    private void patternsEditor(int x, int y, int w, int bottom, CategorySettings cs, int index) {
        Category category = page.category;
        Map<String, Integer> catalog = sc.catalog(category);
        String members = Labels.members(category);
        Form form = form(x, w);
        form.label(y, Ui.H, members, null);
        int fx = form.lx(), fw = form.cw();
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
                    owner == null ? "Make a new group with " + shortId(id) : "Open group " + owner.displayName())) {
                openTextureGroup(cs, id, owner);
                return;
            }
            int original = catalog.get(id);
            ui.swatch(x + 5, ry + 2, 7, 7, original);
            ui.text(">", x + 14, ry + 1, Ui.FAINT);
            ui.swatch(x + 20, ry + 2, 7, 7, chipColor(preview.appearanceFor(new TextureKey(category, id)), original, id));
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
        // First place, so that this look wins over a broader group that also matches the texture.
        setCategory(cs.withRuleAt(0, cs.newGroup(shortId(id), Collections.singletonList(shortId(id)))));
        go(page.withGroup(0));
    }

    /**
     * The servers with the profile each uses: the server the player is on when it has no rule yet, a field to add one,
     * and a row for each rule with a list to pick its profile or Albedo off.
     */
    private void serversPage(int x, int y, int w, int h) {
        // The open list of a server stays where its row was in the last frame; no row above or below it takes its clicks.
        if (mode == Mode.PICK_PROFILE && pickServer != null) ui.reserveOverlay(profileListArea());
        int cy = y + ui.title(x, y, w, "Servers", "") + 3;
        String active = draft.activeProfileName(), here = sc.server();
        ServerRule hereRule = here == null ? null : draft.serverRule(here);
        if (here != null && hereRule == null) {
            String add = "Add this server";
            int bw = ui.buttonWidth(add);
            ui.textFit("This server: " + here, x, cy + 3, w - bw - 6, Ui.TEXT);
            if (ui.button("server.here", x + w - bw, cy, bw, Ui.H, add, Ui.Style.PRIMARY, true, "Add " + here + " with " + active)) {
                setDraft(draft.withServer(new ServerRule(here, active)));
            }
            cy += Ui.H + GAP;
        }
        String add = "Add";
        int bw = ui.buttonWidth(add);
        // Taken before the field, which would end its input on Enter.
        boolean enter = ui.isFocused("server.name") && ui.takeKey(UiKey.ENTER);
        ui.textField("server.name", serverName, x, cy, w - bw - 4, Ui.H, "Server address, for example hypixel.net",
                "An address also covers its subdomains: hypixel.net is also mc.hypixel.net");
        String typed = ServerRule.host(serverName.text);
        boolean listed = draft.servers().containsKey(typed), canAdd = !typed.isEmpty() && !listed;
        if (ui.button("server.add", x + w - bw, cy, bw, Ui.H, add, Ui.Style.NORMAL, canAdd,
                listed ? typed + " is already in the list" : "Add the server with " + active) || enter && canAdd) {
            setDraft(draft.withServer(new ServerRule(typed, active)));
            serverName.set("");
        }
        cy += Ui.H + GAP;
        List<ServerRule> rules = new ArrayList<ServerRule>(draft.servers().values());
        if (rules.isEmpty()) {
            ui.paragraph("No servers.", x, cy + 2, w, Ui.FAINT);
            return;
        }
        int pickW = ui.buttonWidth(OFF) + 10;
        for (String n : draft.profiles().keySet()) pickW = Math.max(pickW, ui.buttonWidth(n) + 10);
        int pw = Math.min(pickW, w / 2);
        String hereServer = hereRule == null ? null : hereRule.server();
        RowList.draw(ui, "servers", x, cy, w, y + h - cy, rules.size(), LIST_ROW,
                (i, rx, ry, rw, rh) -> serverRow(rx, ry, rw, rh, pw, rules.get(i), rules.get(i).server().equals(hereServer)));
    }

    /** One server rule with the list of its profile, which returns true when a click deleted it. */
    private boolean serverRow(int x, int y, int w, int h, int pickW, ServerRule r, boolean here) {
        ui.round(x, y, w, h, Ui.PANEL);
        if (here) ui.fill(x, y + 1, 2, h - 2, Ui.ACCENT);
        int px = x + w - 19 - pickW;
        String tag = here ? "this server" : "";
        int tagW = tag.isEmpty() ? 0 : ui.width(tag) + 6;
        String name = ui.ellipsize(r.server(), px - x - 10 - tagW);
        ui.text(name, x + 5, y + 3, Ui.TEXT);
        if (tagW > 0) ui.textFit(tag, x + 11 + ui.width(name), y + 3, px - x - 16 - ui.width(name), Ui.FAINT);

        Rect button = new Rect(px, y + 1, pickW, 12);
        boolean open = mode == Mode.PICK_PROFILE && r.server().equals(pickServer);
        if (open) {
            pickAnchor = button;
            pickAnchorShown = true;
        }
        if (ui.button("server.profile." + r.server(), px, y + 1, pickW, 12, "", Ui.Style.NORMAL, true, "The profile on " + r.server()
                + ". " + draft.keys().toggle + " and " + draft.keys().cycleProfile + " change it there until you leave.")) {
            openPicker(r.server(), button, open);
            pickAnchorShown = true;
        }
        String label = r.profile() == null ? OFF : r.profile();
        ui.text(ui.ellipsize(label, pickW - 16), px + 5, y + 3, r.profile() == null ? Ui.MUTED : Ui.TEXT);
        ui.text("v", px + pickW - 10, y + 3, Ui.MUTED);

        if (ui.confirmButton("server.del." + r.server(), x + w - 17, y + 1, 15, 12, "x", "Remove " + r.server(),
                "Click again to remove " + r.server())) {
            setDraft(draft.withoutServer(r.server()));
            return true;
        }
        return false;
    }

    private void worldPage(int x, int y, int w, int h) {
        int cy = y + ui.title(x, y, w, "World", "These settings apply to all categories") + 3;
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

    private void footer(int x, int y, int w) {
        boolean dirty = !draft.equals(saved);
        Config.KeyBinds k = draft.keys();
        int bw = Math.max(44, ui.buttonWidth("Discard", "Apply"));
        int lw = ui.buttonWidth("Export", "Import");
        if (ui.button("done", x + w - bw, y, bw, Ui.H, "Done", Ui.Style.PRIMARY, true, "Save and close (Esc)")) done();
        if (ui.button("apply", x + w - 2 * bw - 3, y, bw, Ui.H, "Apply", Ui.Style.NORMAL, dirty,
                dirty ? "Save and show the result. The screen stays open." : "There are no changes to save")) apply();
        if (ui.button("cancel", x + w - 3 * bw - 6, y, bw, Ui.H, dirty ? "Discard" : "Close", Ui.Style.NORMAL, true,
                dirty ? "Close and do not save the changes" : null)) closed = true;
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
    private void toast(int x, int top, int bottom, int w) {
        if (toast == null) return;
        if (System.currentTimeMillis() - toast.at > TOAST_MS) {
            toast = null;
            return;
        }
        int tw = Math.min(w, 240), th = ui.paragraphHeight(toast.text, tw - 12) + 8;
        // The toast goes to the top of the page when it would cover an open dropdown, which the user is working in.
        Rect drop = ui.overlayDrawn();
        boolean covers = drop != null && drop.x < x + tw && x < drop.x + drop.w && drop.y < bottom && bottom - th < drop.bottom();
        int ty = covers ? top : bottom - th;
        ui.panel(new Rect(x, ty, tw, th), toast.error ? Ui.DANGER : Ui.BORDER, Ui.TOOLTIP);
        ui.paragraph(toast.text, x + 6, ty + 4, tw - 12, toast.error ? Ui.DANGER : Ui.TEXT);
    }

    private void say(String text) { toast = new Toast(text, false); }

    private void sayError(String text) { toast = new Toast(text, true); }

    private void exportProfile() {
        try {
            File file = sc.exportProfile(profile());
            say("Saved to profiles/" + file.getName() + " and copied to the clipboard.");
        } catch (IOException e) {
            sc.platform().warn("Cannot export the profile", e);
            sayError("Cannot save the file: " + e.getMessage());
        }
    }

    private void importProfile() {
        String text = sc.platform().clipboard();
        if (text == null || text.trim().isEmpty()) {
            sayError("The clipboard is empty. Copy the text of a profile file, then click Import.");
            return;
        }
        try {
            switchProfile(draft.withImportedProfile(ConfigCodec.decodeProfileFile(text)));
            say("Added profile " + draft.activeProfileName() + ". Click Apply or Done to keep it.");
        } catch (IllegalArgumentException e) {
            sayError("Cannot import: " + e.getMessage());
        }
    }

    private void apply() {
        RefreshResult r = save();
        // A server rule can make the state in effect differ from the draft, so the screen shows what is in effect.
        saved = sc.config();
        setDraft(saved);
        say(r.outcome == RefreshResult.Outcome.RELOADED ? "Saved. Resources reload because " + r.reason + "." : "Saved.");
    }

    private void done() {
        if (mode == Mode.RENAME) finishRename();
        if (!draft.equals(saved)) save();
        closed = true;
    }

    /** Gives the draft to the core as the file stores it, with the on or off state and profile only if the player changed them. */
    private RefreshResult save() {
        Config.State stored = sc.storedConfig().state();
        // A rename of the shown profile also renames the stored one when they are the same profile.
        if (!draft.profiles().containsKey(stored.profile) && stored.profile.equals(saved.activeProfileName())) {
            stored = new Config.State(stored.enabled, draft.activeProfileName());
        }
        Config.State shown = draft.state();
        return sc.apply(draft.withState(stored), shown.equals(saved.state()) ? null : shown);
    }

    private Profile profile() { return draft.activeProfile(); }

    private void setDraft(Config c) {
        changed = true;
        draft = c;
        preview = Policy.of(c.activeProfile());
    }

    /** Selects a different profile, which closes a group of the previous one. */
    private void switchProfile(Config c) {
        setDraft(c);
        skyLayers.reset();
        go(page.withGroup(-1));
    }

    /** A change to a built-in profile goes into a new copy of it (see {@link Config}), and the message says so. */
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
        else setRule(cs, index, cs.rules().get(index).withLook(a, cs.appearance()));
    }

    private void setRule(CategorySettings cs, int index, Rule r) {
        setCategory(cs.withRule(index, r));
    }

    /** The real transform on one pixel of the {@code original} average color with the Chroma hue of {@code id}, or null for hidden. */
    private static Integer chipColor(Appearance a, int original, String id) {
        return a.mode() == Appearance.Mode.HIDDEN ? null : PixelTransformer.preview(a, original, id);
    }
}
