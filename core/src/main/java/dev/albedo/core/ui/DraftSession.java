package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.Policy;
import dev.albedo.core.RefreshResult;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

/** The draft config of the settings screen, which Apply and Done give to {@link Albedo#apply}, and the toast that reports on it. */
final class DraftSession {
    private static final long TOAST_MS = 6000;

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

    private final Albedo albedo;
    private final Ui ui;
    private final Supplier<Category> category;
    private final Runnable relayout;
    private final Runnable switched;
    private Config saved;
    private Config draft;
    private Policy preview;
    private Toast toast;

    /**
     * @param category the category of the open page, which {@link #setCategory} changes
     * @param relayout tells the screen that the draft changed, so that it draws the frame again
     * @param switched runs after a different profile became the draft's profile
     */
    DraftSession(Albedo albedo, Ui ui, Supplier<Category> category, Runnable relayout, Runnable switched) {
        this.albedo = albedo;
        this.ui = ui;
        this.category = category;
        this.relayout = relayout;
        this.switched = switched;
        this.saved = albedo.config();
        set(saved);
    }

    Config draft() { return draft; }

    /** The policy of the draft, for the colors that the texture list shows. */
    Policy preview() { return preview; }

    Profile profile() { return draft.activeProfile(); }

    boolean dirty() { return !draft.equals(saved); }

    void set(Config c) {
        relayout.run();
        draft = c;
        preview = Policy.of(c.activeProfile());
    }

    /** Selects a different profile. */
    void switchProfile(Config c) {
        set(c);
        switched.run();
    }

    /** A change to a built-in profile goes into a new copy of it (see {@link Config}), and the message says so. */
    void setProfile(Profile p) {
        String before = draft.activeProfileName();
        set(draft.withProfile(p));
        if (!draft.activeProfileName().equals(before)) {
            say(before + " is built in and does not change. Your changes are in the new profile " + draft.activeProfileName() + ".");
        }
    }

    void setCategory(CategorySettings cs) { setProfile(profile().with(category.get(), cs)); }

    /** Sets the global look ({@code index} -1) or a group's own look. */
    void setLook(CategorySettings cs, int index, Appearance a) {
        if (index < 0) setCategory(cs.withAppearance(a));
        else setRule(cs, index, cs.rules().get(index).withLook(a, cs.appearance()));
    }

    void setRule(CategorySettings cs, int index, Rule r) {
        setCategory(cs.withRule(index, r));
    }

    void apply() {
        RefreshResult r = save();
        // A server rule can make the state in effect differ from the draft, so the screen shows what is in effect.
        saved = albedo.config();
        set(saved);
        say(r.outcome == RefreshResult.Outcome.RELOADED ? "Saved. Resources reload because " + r.reason + "." : "Saved.");
    }

    void saveIfDirty() {
        if (dirty()) save();
    }

    /** Gives the draft to the core as the file stores it, with the on or off state and profile only if the player changed them. */
    private RefreshResult save() {
        Config.State stored = albedo.storedConfig().state();
        // A rename of the shown profile also renames the stored one when they are the same profile.
        if (!draft.profiles().containsKey(stored.profile) && stored.profile.equals(saved.activeProfileName())) {
            stored = new Config.State(stored.enabled, draft.activeProfileName());
        }
        Config.State shown = draft.state();
        return albedo.apply(draft.withState(stored), shown.equals(saved.state()) ? null : shown);
    }

    void exportProfile() {
        try {
            File file = albedo.exportProfile(profile());
            say("Saved to profiles/" + file.getName() + " and copied to the clipboard.");
        } catch (IOException e) {
            albedo.platform().warn("Cannot export the profile", e);
            sayError("Cannot save the file: " + e.getMessage());
        }
    }

    void importProfile() {
        String text = albedo.platform().clipboard();
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

    void say(String text) { toast = new Toast(text, false); }

    private void sayError(String text) { toast = new Toast(text, true); }

    void hideToast() { toast = null; }

    /** The last message: a box above the footer for a few seconds, wrapped so all of it shows. */
    void drawToast(int x, int top, int bottom, int w) {
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
}
