package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ServerRule;

import java.util.ArrayList;
import java.util.List;

/**
 * The servers with the profile each uses: the server the player is on when it has no rule yet, a field to add one, and a row
 * for each rule with a list to pick its profile or Albedo off.
 */
final class ServersPage {
    private final Ui ui;
    private final Albedo albedo;
    private final DraftSession session;
    private final ProfileHeader header;
    private final TextField serverName = new TextField(64, c -> Character.isLetterOrDigit(c) || ".-:[]".indexOf(c) >= 0);

    /** @param header holds the profile list that a server row opens */
    ServersPage(Ui ui, Albedo albedo, DraftSession session, ProfileHeader header) {
        this.ui = ui;
        this.albedo = albedo;
        this.session = session;
        this.header = header;
    }

    /** Empties the address field, for a different page. */
    void reset() {
        serverName.set("");
    }

    void draw(int x, int y, int w, int h) {
        // The open list of a server stays where its row was in the last frame.
        header.reserveServerList();
        Config draft = session.draft();
        int cy = y + ui.title(x, y, w, "Servers", "") + 3;
        String active = draft.activeProfileName(), here = albedo.server();
        ServerRule hereRule = here == null ? null : draft.serverRule(here);
        if (here != null && hereRule == null) {
            String add = "Add this server";
            int bw = ui.buttonWidth(add);
            ui.textFit("This server: " + here, x, cy + 3, w - bw - 6, Ui.TEXT);
            if (ui.button("server.here", x + w - bw, cy, bw, Ui.H, add, Ui.Style.PRIMARY, true, "Add " + here + " with " + active)) {
                session.set(draft.withServer(new ServerRule(here, active)));
            }
            cy += Ui.H + Ui.GAP;
        }
        String add = "Add";
        int bw = ui.buttonWidth(add);
        // Taken before the field, which would end its input on Enter.
        boolean enter = ui.isFocused("server.name") && ui.takeKey(UiKey.ENTER);
        ui.textField("server.name", serverName, x, cy, w - bw - 4, Ui.H, "Server address, for example hypixel.net",
                "An address also covers its subdomains: hypixel.net is also mc.hypixel.net");
        String typed = ServerRule.host(serverName.text());
        draft = session.draft();
        boolean listed = draft.servers().containsKey(typed), canAdd = !typed.isEmpty() && !listed;
        if (ui.button("server.add", x + w - bw, cy, bw, Ui.H, add, Ui.Style.NORMAL, canAdd,
                listed ? typed + " is already in the list" : "Add the server with " + active) || enter && canAdd) {
            session.set(draft.withServer(new ServerRule(typed, active)));
            serverName.set("");
        }
        cy += Ui.H + Ui.GAP;
        draft = session.draft();
        List<ServerRule> rules = new ArrayList<ServerRule>(draft.servers().values());
        if (rules.isEmpty()) {
            ui.paragraph("No servers.", x, cy + 2, w, Ui.FAINT);
            return;
        }
        int pickW = ui.buttonWidth(ProfilePicker.OFF) + 10;
        for (String n : draft.profiles().keySet()) pickW = Math.max(pickW, ui.buttonWidth(n) + 10);
        int pw = Math.min(pickW, w / 2);
        String hereServer = hereRule == null ? null : hereRule.server();
        RowList.draw(ui, "servers", x, cy, w, y + h - cy, rules.size(), RowList.PANEL_ROW,
                (i, rx, ry, rw, rh) -> row(rx, ry, rw, rh, pw, rules.get(i), rules.get(i).server().equals(hereServer)));
    }

    /** One server rule with the list of its profile, which returns true when a click deleted it. */
    private boolean row(int x, int y, int w, int h, int pickW, ServerRule r, boolean here) {
        ui.round(x, y, w, h, Ui.PANEL);
        if (here) ui.fill(x, y + 1, 2, h - 2, Ui.ACCENT);
        int px = x + w - 19 - pickW;
        String tag = here ? "this server" : "";
        int tagW = tag.isEmpty() ? 0 : ui.width(tag) + 6;
        String name = ui.ellipsize(r.server(), px - x - 10 - tagW);
        ui.text(name, x + 5, y + 3, Ui.TEXT);
        if (tagW > 0) ui.textFit(tag, x + 11 + ui.width(name), y + 3, px - x - 16 - ui.width(name), Ui.FAINT);

        Config.KeyBinds keys = session.draft().keys();
        header.serverButton(r.server(), new Rect(px, y + 1, pickW, 12), "The profile on " + r.server()
                + ". " + keys.toggle + " and " + keys.cycleProfile + " change it there until you leave.");
        String label = r.profile() == null ? ProfilePicker.OFF : r.profile();
        ui.text(ui.ellipsize(label, pickW - 16), px + 5, y + 3, r.profile() == null ? Ui.MUTED : Ui.TEXT);
        ui.text("v", px + pickW - 10, y + 3, Ui.MUTED);

        if (ui.confirmButton("server.del." + r.server(), x + w - 17, y + 1, 15, 12, "x", "Remove " + r.server(),
                "Click again to remove " + r.server())) {
            session.set(session.draft().withoutServer(r.server()));
            return true;
        }
        return false;
    }
}
