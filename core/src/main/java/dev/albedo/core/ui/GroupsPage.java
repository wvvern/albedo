package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.Glob;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

import static dev.albedo.core.ui.GroupMatches.toPattern;

/** The Groups tab of a category, and the page of one group with its look and its textures. */
final class GroupsPage {
    private static final String ADD = "add";
    private static final int ROW = DropdownRows.ROW;

    private final Ui ui;
    private final Albedo albedo;
    private final DraftSession session;
    private final LookSection look;
    private final IntConsumer openGroup;
    private final GroupMatches matches = new GroupMatches();
    private final TextField add = new TextField(64, c -> Character.isLetterOrDigit(c) || "_-:*?/.^$|()[]{}+\\,".indexOf(c) >= 0);
    private final TextField groupName = new TextField(32, TextField.ANY);
    /** Shown under the scope switch when a switch was refused. */
    private String notice;
    private int suggestion;

    /** @param openGroup opens the group of an index of the category, or with -1 goes back to the tab */
    GroupsPage(Ui ui, Albedo albedo, DraftSession session, LookSection look, IntConsumer openGroup) {
        this.ui = ui;
        this.albedo = albedo;
        this.session = session;
        this.look = look;
        this.openGroup = openGroup;
    }

    /** Empties the texture input, for a different group or page. */
    void resetInput() {
        add.set("");
        suggestion = 0;
    }

    void clearNotice() {
        notice = null;
    }

    void tab(int x, int y, int w, int h, Category category, CategorySettings cs) {
        String name = Labels.name(category);
        String[] scopes = {"Everything", "Only groups"};
        Form form = look.form(x, w);
        form.label(y, Ui.H, "Scope", null);
        int scope = ui.segmented("scope", form.lx(), y, form.cw(), scopes, cs.onlyGroups() ? 1 : 0, new String[]{
                "The " + name + " look applies to all. Groups change their own textures.",
                "Only textures in groups change. All other textures stay vanilla."});
        int cy = y + ui.segmentedHeight(scopes, form.cw()) + Ui.GAP;
        if (scope != (cs.onlyGroups() ? 1 : 0)) {
            Rule blocker = cs.blocksScopeSwitch();
            if (blocker == null) {
                session.setCategory(cs.withOnlyGroups(scope == 1));
                notice = null;
            } else {
                notice = "Group \"" + blocker.displayName() + "\" uses " + (cs.onlyGroups() ? "Global" : "Vanilla")
                        + ". This has no meaning in the other scope. Change it first.";
            }
            return;
        }
        if (notice != null) cy += ui.paragraph(notice, x, cy, w, Ui.DANGER) + Ui.GAP;
        String newLabel = "+ New group";
        int nw = ui.buttonWidth(newLabel);
        if (ui.button("group.new", x, cy, nw, Ui.H, newLabel, Ui.Style.PRIMARY, true, "Make a set of textures with its own look")) {
            session.setCategory(cs.withRuleAt(cs.rules().size(), cs.newGroup(freeGroupName(cs), Collections.<String>emptyList())));
            openGroup.accept(cs.rules().size());
            ui.focus(ADD);
            return;
        }
        List<Rule> rules = cs.rules();
        if (rules.size() > 1) ui.textFit("A higher group wins.", x + nw + 6, cy + 3, w - nw - 6, Ui.FAINT);
        cy += Ui.H + Ui.GAP;
        if (rules.isEmpty()) {
            ui.paragraph(cs.onlyGroups() ? "There are no groups. Nothing changes."
                    : "There are no groups. All textures use the " + name + " look.", x, cy + 2, w, Ui.FAINT);
            return;
        }
        RowList.draw(ui, "groups", x, cy, w, y + h - cy, rules.size(), RowList.PANEL_ROW, (i, rx, ry, rw, rh) -> row(rx, ry, rw, rh, cs, i));
    }

    /** One row of the group list, which returns true when a click opened or deleted a group. */
    private boolean row(int x, int y, int w, int h, CategorySettings cs, int i) {
        List<Rule> rules = cs.rules();
        Rule r = rules.get(i);
        int bx = x + w - RowButtons.W;
        ui.round(x, y, w, h, Ui.PANEL);
        if (ui.button("group." + i, x, y, bx - x - 2, h, "", Ui.Style.GHOST, true, "Edit group " + r.displayName())) {
            openGroup.accept(i);
            return true;
        }
        Appearance a = cs.lookOf(r);
        LookSection.icon(ui, x + 4, y + 3, a);
        String lookName = r.usesGlobalLook() ? "global look" : a.isOriginal() ? "vanilla" : Labels.mode(a.mode()).toLowerCase(Locale.ROOT);
        int room = bx - x - 22;
        int nameW = Math.min(ui.width(r.displayName()), room / 2);
        ui.text(ui.ellipsize(r.displayName(), nameW), x + 16, y + 3, Ui.TEXT);
        String members = r.patterns().isEmpty() ? "no textures" : String.join(", ", r.patterns());
        ui.text(ui.ellipsize(lookName + ": " + members, room - nameW - 6), x + 22 + nameW, y + 3, Ui.MUTED);
        switch (RowButtons.draw(ui, "group", String.valueOf(i), bx, y, i > 0, i < rules.size() - 1, "Move up. A higher group wins.",
                "Move down", "Delete this group", "Click again to delete \"" + r.displayName() + "\"")) {
            case UP: session.setCategory(cs.withRuleMoved(i, -1)); return false;
            case DOWN: session.setCategory(cs.withRuleMoved(i, 1)); return false;
            case DELETE: session.setCategory(cs.withoutRule(i)); return true;
            default: return false;
        }
    }

    /** The page of the group {@code index}: its name, its look and its textures. */
    void page(int x, int y, int w, int bottom, Category category, CategorySettings cs, int index) {
        Rule rule = cs.rules().get(index);
        if (ui.backButton("back", x, y - 3, "Back to the groups (Esc)")) {
            openGroup.accept(-1);
            return;
        }
        String crumb = Labels.name(category) + " /";
        ui.text(crumb, x + 16, y, Ui.MUTED);
        int dw = ui.buttonWidth("Delete");
        int nx = x + 20 + ui.width(crumb), nw = Math.min(140, x + w - dw - 6 - nx);
        if (!ui.isFocused("group.name")) groupName.set(rule.displayName());
        if (ui.textField("group.name", groupName, nx, y - 3, nw, Ui.H, "Group name", "The name of this group")
                && !groupName.text.trim().isEmpty()) {
            session.setRule(cs, index, rule.withName(groupName.text.trim()));
            return;
        }
        if (ui.confirmButton("group.delete", x + w - dw, y - 3, dw, Ui.H, "Delete", "Delete this group",
                "Click again to delete \"" + rule.displayName() + "\"")) {
            session.setCategory(cs.withoutRule(index));
            openGroup.accept(-1);
            return;
        }
        // The look scrolls above the room for the block input and one line of chips, which the input's suggestions cover.
        int top = y + Ui.H + 2, viewH = bottom - Ui.H - 16 - top;
        int cy = Column.draw(ui, "look", new Rect(x, top, w, viewH), look.items(category, cs, index, w, viewH, 0));
        patternsEditor(x, cy, w, bottom, category, cs, index);
    }

    /** The group's patterns as removable chips, and an input that suggests matching textures while typing. */
    private void patternsEditor(int x, int y, int w, int bottom, Category category, CategorySettings cs, int index) {
        Map<String, Integer> catalog = albedo.catalog(category);
        String members = Labels.members(category);
        Form form = look.form(x, w);
        form.label(y, Ui.H, members, null);
        int fx = form.lx(), fw = form.cw();
        // Keys the list reacts to are taken before the field would (Enter would unfocus it); typing goes to the field.
        boolean focused = ui.isFocused(ADD);
        boolean down = focused && ui.takeKey(UiKey.DOWN), up = focused && ui.takeKey(UiKey.UP);
        boolean complete = focused && ui.takeKey(UiKey.TAB), enter = focused && ui.takeKey(UiKey.ENTER);
        if (ui.textField(ADD, add, fx, y, fw, Ui.H, "Type a name, for example " + Labels.example(category) + ". * selects all.",
                "Enter adds the selected match. Tab completes. * matches any text. /text/ is a regular expression.")) {
            suggestion = 0;
        }
        focused = ui.isFocused(ADD);
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
                session.setRule(cs, index, rule.withPatterns(next));
                return true;
            }
            cx += cw + 3;
        }
        return false;
    }

    /** The suggestions under the group input, drawn over the chips. */
    private void suggestionList(Rect area, List<GroupMatches.Suggestion> rows, CategorySettings cs, int index) {
        int fx = area.x, fw = area.w;
        DropdownRows.open(ui, area);
        int clicked = DropdownRows.draw(ui, new Rect(fx + 1, area.y + 1, fw - 2, rows.size() * ROW), 0, rows.size(), suggestion, -1,
                i -> "sugg." + i, (i, rx, ry, rw) -> {
                    GroupMatches.Suggestion s = rows.get(i);
                    ui.swatch(fx + 4, ry + 2, 7, 7, s.color);
                    String right = s.owner != null ? "in " + s.owner : s.count > 1 ? s.count + " textures" : "";
                    int tw = right.isEmpty() ? 0 : Math.min(ui.width(right), fw / 3) + 6;
                    ui.text(ui.ellipsize(s.pattern, fw - 20 - tw), fx + 15, ry + 1, Ui.TEXT);
                    if (tw > 0) ui.text(ui.ellipsize(right, tw - 6), fx + fw - tw - 2, ry + 1, s.owner != null ? Ui.WARN : Ui.FAINT);
                    if (s.owner != null) ui.tip(rx, ry, rw, ROW, "Group " + s.owner + " also has this. A higher group wins.");
                });
        ui.endOverlay();
        if (clicked >= 0) addPattern(cs, index, rows.get(clicked).pattern);
    }

    private void addPattern(CategorySettings cs, int index, String pattern) {
        add.set("");
        suggestion = 0;
        if (!pattern.isEmpty()) session.setCategory(cs.withPatternIn(index, pattern));
        ui.focus(ADD);
    }

    private static String freeGroupName(CategorySettings cs) {
        for (int n = cs.rules().size() + 1; ; n++) {
            boolean taken = false;
            for (Rule r : cs.rules()) if (r.displayName().equals("Group " + n)) taken = true;
            if (!taken) return "Group " + n;
        }
    }
}
