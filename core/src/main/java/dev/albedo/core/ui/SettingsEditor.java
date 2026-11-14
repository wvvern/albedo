package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Toggle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The settings screen of all versions, which edits a draft config that Apply and Done give to {@link Albedo#apply}. */
public final class SettingsEditor {
    static final Category[] CATEGORIES = {
            Category.BLOCKS, Category.FLUIDS, Category.BLOCK_ENTITIES, Category.DECORATIONS, Category.ITEMS, Category.ENTITIES,
            Category.PLAYERS, Category.PARTICLES, Category.BLOCK_BREAKING, Category.SKY, Category.CLOUDS, Category.WEATHER,
    };
    private static final List<String> NAMES = new ArrayList<String>();
    static {
        for (Category c : CATEGORIES) NAMES.add(Labels.name(c));
    }
    private static final int MAX_W = 620, MAX_H = 380;

    private enum Tab { LOOK, GROUPS, TEXTURES }

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

    private final Albedo albedo;
    final Ui ui;
    private final ScriptedInput script = new ScriptedInput();
    private final Frame frame = new Frame();
    /** Set when this frame changed the draft, the page or the mode. */
    private boolean changed;
    private Page page = Page.of(Category.BLOCKS);
    private boolean closed;
    private final SkyLayersEditor skyLayers;
    private final DraftSession session;
    private final ProfileHeader header;
    private final LookSection look;
    private final GroupsPage groups;
    private final TexturesPage textures;
    private final ServersPage servers;

    public SettingsEditor(final Albedo albedo) {
        this.albedo = albedo;
        this.ui = new Ui(() -> albedo.platform().clipboard());
        this.skyLayers = new SkyLayersEditor(ui, this::relayout, e -> albedo.platform().warn("Cannot make the sky preview", e));
        this.session = new DraftSession(albedo, ui, () -> page.category, this::relayout, this::profileSwitched);
        this.header = new ProfileHeader(ui, session, this::relayout);
        this.look = new LookSection(ui, albedo, session, skyLayers, this::relayout);
        this.groups = new GroupsPage(ui, albedo, session, look, this::openGroup);
        this.textures = new TexturesPage(ui, albedo, session, this::openGroup);
        this.servers = new ServersPage(ui, albedo, session, header);
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

    /** Tells the frame that the draft, the page or the mode changed. */
    private void relayout() {
        changed = true;
    }

    private void draw(Canvas canvas, int mouseX, int mouseY) {
        ui.begin(canvas, mouseX, mouseY);
        if (ui.clicked()) session.hideToast();

        int w = canvas.width(), h = canvas.height();
        look.measure();
        int pw = Math.min(w - 2 * (w < 440 ? 4 : 10), MAX_W), ph = Math.min(h - 2 * (h < 260 ? 4 : 10), MAX_H);
        int x0 = (w - pw) / 2, y0 = (h - ph) / 2, y1 = y0 + ph;
        ui.fill(0, 0, w, h, Ui.SCRIM);
        ui.round(x0, y0, pw, ph, Ui.BG);
        ui.tipBounds(new Rect(x0, y0, pw, ph));

        int bodyTop = y0 + 26, bodyBottom = y1 - 24;
        header.beginFrame(new Rect(x0 + 8, bodyTop, pw - 16, bodyBottom - bodyTop));
        header.draw(x0 + 8, y0 + 6, pw - 16);
        ui.fill(x0 + 8, bodyTop - 3, pw - 16, 1, Ui.BORDER);
        int sideW = Math.min(ui.widest(NAMES) + 22, pw / 4 + 20);
        sidebar(x0 + 8, bodyTop + 2, sideW, bodyBottom - bodyTop - 4);
        int cx = x0 + 16 + sideW, cw = pw - 24 - sideW;
        content(cx, bodyTop + 2, cw, bodyBottom - bodyTop - 4);
        ui.fill(x0 + 8, bodyBottom + 1, pw - 16, 1, Ui.BORDER);
        footer(x0 + 8, bodyBottom + 5, pw - 16);
        session.drawToast(cx, bodyTop + 2, bodyBottom - 3, cw);
        header.endFrame();
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

    /** The only way the page changes: what belongs to the old page (input, suggestion, notice, search) is reset. */
    private void go(Page next) {
        Page prev = page;
        page = next;
        changed = true;
        if (next.group != prev.group || next.view != prev.view || next.category != prev.category) {
            groups.resetInput();
            servers.reset();
            ui.clearFocus();
        }
        boolean otherCategory = next.category != prev.category;
        if (otherCategory || next.view != prev.view || next.tab != prev.tab) {
            textures.reset();
            groups.clearNotice();
        }
        if (otherCategory) ui.resetScroll("groups");
        if (otherCategory || next.view != prev.view || next.tab != prev.tab || next.group != prev.group) ui.resetScroll("look");
        if (otherCategory || next.view != prev.view) skyLayers.reset();
    }

    /** Opens a group of the category, or with -1 goes back to the tab it was opened from. */
    private void openGroup(int index) {
        go(page.withGroup(index));
    }

    /** Closes a group of the previous profile. */
    private void profileSwitched() {
        skyLayers.reset();
        go(page.withGroup(-1));
    }

    private void sidebar(int x, int y, int w, int h) {
        // Rows shrink a little on short windows so that the World and Servers entries always show.
        int pitch = Math.max(10, Math.min(13, (h - 11) / (CATEGORIES.length + 2)));
        int ry = y;
        for (Category c : CATEGORIES) {
            Appearance a = session.profile().get(c).appearance();
            String tip = Labels.description(c) + (a.isOriginal() ? " Now: vanilla." : "");
            boolean active = page.view == View.CATEGORY && c == page.category;
            if (sidebarRow("cat." + c.key(), x, ry, w, pitch - 1, Labels.name(c), tip, active, !a.isOriginal())) go(Page.of(c));
            LookSection.icon(ui, x + 6, ry + (pitch - 9) / 2, a);
            ry += pitch;
        }
        ry += 5;
        ui.fill(x + 4, ry - 3, w - 8, 1, Ui.BORDER);
        if (sidebarRow("world", x, ry, w, pitch - 1, "World", "Fullbright, shading, foliage, shadows, armor and capes",
                page.view == View.WORLD, true)) {
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
            servers.draw(x, y, w, h);
            return;
        }
        Category category = page.category;
        CategorySettings cs = session.profile().get(category);
        if (page.inGroup()) {
            // The group may be gone: deleted, or a different profile is active.
            if (page.group < cs.rules().size()) {
                groups.page(x, y, w, y + h, category, cs, page.group);
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
                ? new String[]{"Look", "Groups " + cs.rules().size(), "Textures " + albedo.catalog(category).size()} : new String[]{"Look"};
        String[] tips = category.hasGroups()
                ? new String[]{lookTip, "Sets of textures that have their own look", "All loaded textures. Click one to put it in a group."}
                : new String[]{lookTip};
        int chosen = ui.tabs("tab", x, cy, w, labels, tab.ordinal(), tips);
        if (chosen != tab.ordinal()) {
            go(page.withTab(Tab.values()[chosen]));
            return;
        }
        cy += Ui.H + Ui.GAP;
        switch (tab) {
            case GROUPS: groups.tab(x, cy, w, y + h - cy, category, cs); break;
            case TEXTURES: textures.draw(x, cy, w, y + h - cy, category, cs); break;
            default: look.tab(x, cy, w, y + h, category, cs);
        }
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
            boolean on = session.profile().is(t);
            if (ui.toggleRow(t.key, x + 2, cy + 2, w - 4, Labels.toggle(t), on, explain) != on) session.setProfile(session.profile().with(t, !on));
            if (full) ui.paragraph(explain, x + 6, cy + 15, w - 12, Ui.MUTED);
            cy += rh + 4;
        }
    }

    private void footer(int x, int y, int w) {
        boolean dirty = session.dirty();
        Config.KeyBinds k = session.draft().keys();
        int bw = Math.max(44, ui.buttonWidth("Discard", "Apply"));
        int lw = ui.buttonWidth("Export", "Import");
        if (ui.button("done", x + w - bw, y, bw, Ui.H, "Done", Ui.Style.PRIMARY, true, "Save and close (Esc)")) done();
        if (ui.button("apply", x + w - 2 * bw - 3, y, bw, Ui.H, "Apply", Ui.Style.NORMAL, dirty,
                dirty ? "Save and show the result. The screen stays open." : "There are no changes to save")) session.apply();
        if (ui.button("cancel", x + w - 3 * bw - 6, y, bw, Ui.H, dirty ? "Discard" : "Close", Ui.Style.NORMAL, true,
                dirty ? "Close and do not save the changes" : null)) closed = true;
        if (ui.button("export", x, y, lw, Ui.H, "Export", Ui.Style.NORMAL, true,
                "Save this profile to a file and copy it to the clipboard")) {
            session.exportProfile();
        }
        if (ui.button("import", x + lw + 3, y, lw, Ui.H, "Import", Ui.Style.NORMAL, true,
                "Add the profile that is on the clipboard")) {
            session.importProfile();
        }
        int tx = x + 2 * lw + 3 + 8, room = x + w - 3 * bw - 6 - 8 - tx;
        if (dirty) {
            ui.textFirstFit(tx, y + 3, room, Ui.WARN, "Changes not saved", "Not saved");
        } else {
            ui.textFirstFit(tx, y + 3, room, Ui.FAINT, k.toggle + " on/off   " + k.cycleProfile + " next profile   " + k.settings + " this screen",
                    k.toggle + " on/off   " + k.cycleProfile + " next");
        }
    }

    private void done() {
        header.endRename();
        session.saveIfDirty();
        closed = true;
    }
}
