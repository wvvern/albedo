package dev.singlecolor.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The whole user configuration. Immutable; edits produce a new instance. */
public final class Config {
    private final boolean enabled;
    private final String activeProfile;
    private final String toggleKey;
    private final String cycleKey;
    private final Map<String, Profile> profiles;

    public Config(boolean enabled, String activeProfile, String toggleKey, String cycleKey, Map<String, Profile> profiles) {
        if (profiles.isEmpty()) throw new IllegalArgumentException("config needs at least one profile");
        this.enabled = enabled;
        this.toggleKey = toggleKey;
        this.cycleKey = cycleKey;
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<String, Profile>(profiles));
        this.activeProfile = profiles.containsKey(activeProfile) ? activeProfile : profiles.keySet().iterator().next();
    }

    public boolean enabled() { return enabled; }
    public String activeProfileName() { return activeProfile; }
    public Profile activeProfile() { return profiles.get(activeProfile); }
    public String toggleKey() { return toggleKey; }
    public String cycleKey() { return cycleKey; }
    public Map<String, Profile> profiles() { return profiles; }

    public Config withEnabled(boolean value) {
        return new Config(value, activeProfile, toggleKey, cycleKey, profiles);
    }

    public Config withActiveProfile(String name) {
        return new Config(enabled, name, toggleKey, cycleKey, profiles);
    }

    /** The profile after the active one, wrapping around. */
    public String nextProfileName() {
        List<String> names = new ArrayList<String>(profiles.keySet());
        int i = names.indexOf(activeProfile);
        return names.get((i + 1) % names.size());
    }
}
