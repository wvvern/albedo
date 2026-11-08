package dev.blockpainter.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The whole user configuration. Immutable; edits produce a new instance. */
public final class Config {
    /** Key names (see {@link dev.blockpainter.core.Keys}); "NONE" disables a key. */
    public static final class KeyBinds {
        public static final KeyBinds DEFAULT = new KeyBinds("F6", "F7", "F9");

        public final String toggle;
        public final String cycleProfile;
        public final String settings;

        public KeyBinds(String toggle, String cycleProfile, String settings) {
            this.toggle = toggle;
            this.cycleProfile = cycleProfile;
            this.settings = settings;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof KeyBinds)) return false;
            KeyBinds k = (KeyBinds) o;
            return k.toggle.equals(toggle) && k.cycleProfile.equals(cycleProfile) && k.settings.equals(settings);
        }

        @Override
        public int hashCode() { return (toggle.hashCode() * 31 + cycleProfile.hashCode()) * 31 + settings.hashCode(); }
    }

    private final boolean enabled;
    private final String activeProfile;
    private final KeyBinds keys;
    private final Map<String, Profile> profiles;

    public Config(boolean enabled, String activeProfile, KeyBinds keys, Map<String, Profile> profiles) {
        if (profiles.isEmpty()) throw new IllegalArgumentException("config needs at least one profile");
        this.enabled = enabled;
        this.keys = keys;
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<String, Profile>(profiles));
        this.activeProfile = profiles.containsKey(activeProfile) ? activeProfile : profiles.keySet().iterator().next();
    }

    public static Config defaults() {
        return new Config(true, "quake-white", KeyBinds.DEFAULT, Presets.all());
    }

    public boolean enabled() { return enabled; }
    public String activeProfileName() { return activeProfile; }
    public Profile activeProfile() { return profiles.get(activeProfile); }
    public KeyBinds keys() { return keys; }
    public Map<String, Profile> profiles() { return profiles; }

    public Config withEnabled(boolean value) {
        return new Config(value, activeProfile, keys, profiles);
    }

    public Config withActiveProfile(String name) {
        return new Config(enabled, name, keys, profiles);
    }

    /** Replaces the profile with the same name, or appends it. */
    public Config withProfile(Profile p) {
        Map<String, Profile> next = new LinkedHashMap<String, Profile>(profiles);
        next.put(p.name(), p);
        return new Config(enabled, activeProfile, keys, next);
    }

    /** Profile names are lower-case words joined by "-", e.g. "my-profile". */
    public static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", "-").toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Renames in place, keeping the profile's position and active state. The name is normalized, and a name already
     * in use gets a number ("quake-white-2"). A blank name changes nothing.
     */
    public Config withRenamedProfile(String from, String newName) {
        String clean = normalizeName(newName);
        if (!profiles.containsKey(from) || clean.isEmpty() || clean.equals(from)) return this;
        String to = freeProfileName(clean);
        Map<String, Profile> next = new LinkedHashMap<String, Profile>();
        for (Profile p : profiles.values()) next.put(p.name().equals(from) ? to : p.name(), p.name().equals(from) ? p.renamed(to) : p);
        return new Config(enabled, activeProfile.equals(from) ? to : activeProfile, keys, next);
    }

    /** No-op for the last remaining profile. */
    public Config withoutProfile(String name) {
        if (profiles.size() <= 1) return this;
        Map<String, Profile> next = new LinkedHashMap<String, Profile>(profiles);
        next.remove(name);
        return new Config(enabled, activeProfile, keys, next);
    }

    /** The profile after the active one, wrapping around ({@code step} may be negative). */
    public String profileNameAt(int step) {
        List<String> names = new ArrayList<String>(profiles.keySet());
        int i = names.indexOf(activeProfile);
        return names.get(((i + step) % names.size() + names.size()) % names.size());
    }

    /** A name not used yet, based on {@code base}. */
    public String freeProfileName(String base) {
        if (!profiles.containsKey(base)) return base;
        for (int i = 2; ; i++) if (!profiles.containsKey(base + "-" + i)) return base + "-" + i;
    }

    /** Whether switching from this config to {@code other} keeps textures and chunk meshes (no resource reload needed). */
    public boolean sameRendering(Config other) {
        if (enabled != other.enabled) return false;
        return !enabled || activeProfile().sameRendering(other.activeProfile());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Config)) return false;
        Config c = (Config) o;
        return c.enabled == enabled && c.activeProfile.equals(activeProfile) && c.keys.equals(keys)
                && c.profiles.equals(profiles);
    }

    @Override
    public int hashCode() { return profiles.hashCode() * 31 + activeProfile.hashCode(); }
}
