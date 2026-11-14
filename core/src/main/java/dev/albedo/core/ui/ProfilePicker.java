package dev.albedo.core.ui;

import dev.albedo.core.config.Config;
import dev.albedo.core.config.ServerRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The searchable profile list under the profile button of the header or of a server row. */
final class ProfilePicker {
    /** The choice in the list of a server that turns Albedo off there, which no profile name can be (it has a space). */
    static final String OFF = "Albedo off";
    static final String BUILT_IN = "built-in";
    private static final String SEARCH = "profiles.search";
    private static final int ROW = DropdownRows.ROW;

    private final Ui ui;
    private final DraftSession session;
    private final TextField search = new TextField(32, TextField.ANY);
    /** The server whose profile the list picks, or null when it picks the profile of the screen. */
    String server;
    /** The button that the list belongs to. */
    Rect anchor = new Rect(0, 0, 0, 0);
    /** Whether the button of {@link #server} showed in this frame, so that the list closes when its row scrolls away. */
    boolean anchorShown;
    /** The top and bottom of the page that the list must stay inside. */
    int bodyTop, bodyBottom;
    private int picked;

    ProfilePicker(Ui ui, DraftSession session) {
        this.ui = ui;
        this.session = session;
    }

    /** Starts the list with an empty search on the name in effect, with the keyboard in the search. */
    void open() {
        search.set("");
        picked = Math.max(0, names().indexOf(pickedNow()));
        ui.focus(SEARCH);
    }

    /** Gives back the focus and the overlay of the list. */
    void close() {
        ui.releaseFocus(SEARCH); // a click on Rename has just focused its own field
        ui.endOverlay();
        server = null;
    }

    /** The name the list marks as chosen: the profile of the screen, or the profile of the server ({@link #OFF} for none). */
    private String pickedNow() {
        Config draft = session.draft();
        if (server == null) return draft.activeProfileName();
        ServerRule r = draft.servers().get(server);
        return r == null || r.profile() == null ? OFF : r.profile();
    }

    /** The names in the list that match the search, with {@link #OFF} first for a server. */
    private List<String> names() {
        String q = search.text.trim().toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<String>();
        if (server != null && OFF.toLowerCase(Locale.ROOT).contains(q)) names.add(OFF);
        for (String n : session.draft().profiles().keySet()) if (n.contains(q)) names.add(n);
        return names;
    }

    /** The list under its button, or above it when there is more room there. */
    Rect area() {
        Config draft = session.draft();
        int need = Math.max(1, server == null ? draft.profiles().size() : draft.profiles().size() + 1);
        int below = (bodyBottom - anchor.bottom() - 1 - Ui.H - 6) / ROW, above = (anchor.y - 1 - bodyTop - Ui.H - 6) / ROW;
        boolean up = below < need && above > below;
        int rows = Math.max(1, Math.min(need, up ? above : below)), h = Ui.H + 6 + rows * ROW;
        // As wide as the longest name with its tag, ending where the button ends.
        int w = ui.width(OFF) + 16;
        for (String n : draft.profiles().keySet()) w = Math.max(w, ui.width(n) + (Config.isBuiltIn(n) ? ui.width(BUILT_IN) + 6 : 0) + 16);
        w = Math.max(anchor.w, Math.min(w, anchor.x + anchor.w - 4));
        return new Rect(anchor.x + anchor.w - w, up ? anchor.y - 1 - h : anchor.bottom() + 1, w, h);
    }

    /**
     * Draws the list over the page, where Up, Down and Enter choose and Esc or a click outside closes it.
     *
     * @return true when the list closes
     */
    boolean draw() {
        if (ui.takeKey(UiKey.ESCAPE)) return true;
        Rect area = area();
        int top = Math.min(area.y, anchor.y), bottom = Math.max(area.bottom(), anchor.bottom());
        if (ui.clicked() && !ui.pointerIn(area.x, top, area.w, bottom - top)) return true;
        boolean down = ui.takeKey(UiKey.DOWN), up = ui.takeKey(UiKey.UP), enter = ui.takeKey(UiKey.ENTER);
        int rows = (area.h - Ui.H - 6) / ROW, x = area.x, y = area.y, w = area.w;
        String now = pickedNow();
        DropdownRows.open(ui, area);
        if (ui.textField(SEARCH, search, x + 2, y + 2, w - 4, Ui.H, "Search profiles", null)) picked = 0;
        ui.focus(SEARCH); // the list keeps the keyboard while it is open
        List<String> names = names(); // after the field, so that this frame's typing filters the list
        if (down) picked++;
        if (up) picked--;
        picked = Math.max(0, Math.min(picked, names.size() - 1));
        int ly = y + Ui.H + 4;
        if (names.isEmpty()) ui.textFit("No profile has this name", x + 5, ly + 1, w - 10, Ui.FAINT);
        int first = names.size() > rows ? Math.max(0, Math.min(picked - rows + 1, names.size() - rows)) : 0;
        int clicked = DropdownRows.draw(ui, new Rect(x + 1, ly, w - 2, rows * ROW), first, names.size(), picked, names.indexOf(now),
                i -> "profiles.row." + names.get(i), (i, rx, ry, rw) -> {
                    String n = names.get(i);
                    String tag = Config.isBuiltIn(n) ? BUILT_IN : "";
                    int tagW = tag.isEmpty() ? 0 : ui.width(tag) + 6;
                    ui.text(ui.ellipsize(n, w - 14 - tagW), x + 6, ry + 1, n.equals(OFF) ? Ui.MUTED : Ui.TEXT);
                    if (tagW > 0) ui.text(tag, x + w - 4 - tagW, ry + 1, Ui.FAINT);
                });
        ui.endOverlay();
        // A click and an Enter in one frame choose the lower row.
        int chosen = Math.max(clicked, enter && !names.isEmpty() ? picked : -1);
        if (chosen < 0) return false;
        String name = names.get(chosen);
        if (server == null) session.switchProfile(session.draft().withActiveProfile(name));
        else session.set(session.draft().withServer(new ServerRule(server, name.equals(OFF) ? null : name)));
        return true;
    }
}
