package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.config.Config;

/** The header of the settings screen: the on and off switch, the profile button with its list, and Copy, Rename and Delete. */
final class ProfileHeader {
    /** The header's mode, which only {@link #setMode} changes, so that each mode gives back the focus it took. */
    private enum Mode {
        BROWSE,
        /** The profile list is open under the profile button, or under the profile of a server. */
        PICK_PROFILE,
        /** The profile name is a text field, with Save and Cancel. */
        RENAME
    }

    private final Ui ui;
    private final DraftSession session;
    private final Runnable relayout;
    private final TextField rename = new TextField(32, c -> Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ');
    private Mode mode = Mode.BROWSE;
    /** The open profile list, which is null in all modes but {@link Mode#PICK_PROFILE}. */
    private ProfilePicker picker;
    /** The page of this frame, which an open list must stay inside. */
    private Rect body = new Rect(0, 0, 0, 0);

    /** @param relayout tells the screen that the mode changed */
    ProfileHeader(Ui ui, DraftSession session, Runnable relayout) {
        this.ui = ui;
        this.session = session;
        this.relayout = relayout;
    }

    /** Starts a frame, where an open list must stay inside {@code page}. */
    void beginFrame(Rect page) {
        body = page;
        if (picker == null) return;
        picker.body = page;
        picker.anchorShown = false;
    }

    /** Ends a frame: closes the list of a server whose row did not show, then draws the open list over the page. */
    void endFrame() {
        if (picker != null && picker.server != null && !picker.anchorShown) setMode(Mode.BROWSE);
        if (picker != null && picker.draw()) setMode(Mode.BROWSE);
    }

    /** Keeps the typed name, for Done. */
    void endRename() {
        if (mode == Mode.RENAME) finishRename();
    }

    private void setMode(Mode next) {
        setMode(next, null, null);
    }

    /**
     * The only way the mode changes: the old mode gives back its focus and overlay, the new one sets its own.
     *
     * @param server the server whose profile a new list picks, or null for the profile of the screen
     * @param anchor the button of a new list
     */
    private void setMode(Mode next, String server, Rect anchor) {
        if (picker != null) {
            picker.close();
            picker = null;
        }
        if (mode == Mode.RENAME) ui.releaseFocus("rename");
        mode = next;
        relayout.run();
        if (next == Mode.PICK_PROFILE) {
            picker = new ProfilePicker(ui, session, server, anchor, body);
        } else if (next == Mode.RENAME) {
            rename.set(session.draft().activeProfileName());
            rename.selectAll = true;
            ui.focus("rename");
        }
    }

    void draw(int x, int y, int w) {
        Config draft = session.draft();
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
        if (on != draft.enabled()) session.set(draft.withEnabled(on));
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
        Config draft = session.draft();
        session.set(draft.withRenamedProfile(draft.activeProfileName(), rename.text));
    }

    private void profileBar(int px, int y, int pw, int right, int bw) {
        Config draft = session.draft();
        boolean builtIn = Config.isBuiltIn(draft.activeProfileName());
        if (builtIn) {
            ui.button("delete", right - bw, y, bw, Ui.H, "Delete", Ui.Style.DANGER, false, "Built-in profiles cannot be deleted");
        } else if (ui.confirmButton("delete", right - bw, y, bw, Ui.H, "Delete", "Delete this profile",
                "Click again to delete profile \"" + draft.activeProfileName() + "\"")) {
            String gone = draft.activeProfileName();
            session.switchProfile(draft.withoutProfile(gone));
            session.say("Deleted profile " + gone + ".");
        }
        if (ui.button("rename", right - 2 * bw - 3, y, bw, Ui.H, "Rename", Ui.Style.NORMAL, !builtIn,
                builtIn ? "Built-in profiles cannot be renamed. Copy it to make your own." : "Rename this profile")) {
            setMode(Mode.RENAME);
        }
        if (ui.button("new", right - 3 * bw - 6, y, bw, Ui.H, "Copy", Ui.Style.NORMAL, true,
                "Make a new profile as a copy of this one")) {
            Config now = session.draft();
            String name = now.freeProfileName(now.activeProfileName() + "-copy");
            session.set(now.withProfile(now.activeProfile().renamed(name)).withActiveProfile(name));
            setMode(Mode.RENAME);
        }
        if (mode == Mode.RENAME || pw < 40) return;

        draft = session.draft();
        boolean listHere = picker != null && picker.server == null;
        Rect button = new Rect(px, y, pw, Ui.H);
        if (listHere) picker.anchor = button;
        if (ui.button("profiles", px, y, pw, Ui.H, "", Ui.Style.NORMAL, true,
                "Select a profile. " + draft.keys().cycleProfile + " selects the next profile in game.")) {
            openPicker(null, button, listHere);
            listHere = picker != null;
        }
        String name = draft.activeProfileName(), tag = builtIn ? ProfilePicker.BUILT_IN : "";
        int tagW = tag.isEmpty() ? 0 : ui.width(tag) + 6, room = pw - 18 - tagW;
        String full = "Profile: " + name;
        ui.text(ui.width(full) <= room ? full : ui.ellipsize(name, room), px + 5, y + 3, Ui.TEXT);
        if (tagW > 0) ui.text(tag, px + pw - 12 - tagW, y + 3, Ui.FAINT);
        ui.text("v", px + pw - 10, y + 3, Ui.MUTED);
        // Reserved before the page is drawn, so that a click on the open list does not also reach the page.
        if (listHere) ui.reserveOverlay(picker.area());
    }

    /** Opens the profile list for a server (null: the profile of the screen), or closes it when it is open there. */
    private void openPicker(String server, Rect anchor, boolean openHere) {
        setMode(openHere ? Mode.BROWSE : Mode.PICK_PROFILE, server, anchor);
    }

    /** Reserves the open list of a server before the page draws, so that no row above or below it takes its clicks. */
    void reserveServerList() {
        if (picker != null && picker.server != null) ui.reserveOverlay(picker.area());
    }

    /** The profile button of a server row, which opens or closes its list on a click. */
    void serverButton(String server, Rect button, String tip) {
        boolean open = picker != null && server.equals(picker.server);
        if (open) {
            picker.anchor = button;
            picker.anchorShown = true;
        }
        if (ui.button("server.profile." + server, button.x, button.y, button.w, button.h, "", Ui.Style.NORMAL, true, tip)) {
            openPicker(server, button, open);
            if (picker != null) picker.anchorShown = true;
        }
    }
}
