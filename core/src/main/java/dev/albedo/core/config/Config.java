package dev.albedo.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The whole user configuration. Immutable; edits produce a new instance. */
public final class Config {
    /** Key names (see {@link dev.albedo.core.Keys}); "NONE" disables a key. */
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

    /**
     * The built-in profiles always come first and cannot change. A profile with a built-in name that differs from the
     * built-in one is a change to it: it is kept as a new profile ("quake-white-custom"), which becomes active if the
     * built-in one was.
     */
    public Config(boolean enabled, String activeProfile, KeyBinds keys, Map<String, Profile> profiles) {
        Map<String, Profile> all = new LinkedHashMap<String, Profile>(Presets.all());
        String active = activeProfile;
        for (Profile p : profiles.values()) {
            if (!Presets.isBuiltIn(p.name())) {
                all.put(p.name(), p);
            } else if (!p.equals(Presets.all().get(p.name()))) {
                String copy = freeName(all, p.name() + "-custom");
                all.put(copy, p.renamed(copy));
                if (p.name().equals(active)) active = copy;
            }
        }
        this.enabled = enabled;
        this.keys = keys;
        this.profiles = Collections.unmodifiableMap(all);
        this.activeProfile = all.containsKey(active) ? active : all.keySet().iterator().next();
    }

    public static Config defaults() {
        return new Config(true, "quake-white", KeyBinds.DEFAULT, Collections.<String, Profile>emptyMap());
    }

    public static boolean isBuiltIn(String name) {
        return Presets.isBuiltIn(name);
    }

    /** The profiles the user made: what the config file stores. */
    public Map<String, Profile> userProfiles() {
        Map<String, Profile> out = new LinkedHashMap<String, Profile>(profiles);
        out.keySet().removeAll(Presets.all().keySet());
        return out;
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
     * in use gets a number ("quake-white-2"). A blank name and a built-in profile change nothing.
     */
    public Config withRenamedProfile(String from, String newName) {
        String clean = normalizeName(newName);
        if (!profiles.containsKey(from) || isBuiltIn(from) || clean.isEmpty() || clean.equals(from)) return this;
        String to = freeProfileName(clean);
        Map<String, Profile> next = new LinkedHashMap<String, Profile>();
        for (Profile p : profiles.values()) next.put(p.name().equals(from) ? to : p.name(), p.name().equals(from) ? p.renamed(to) : p);
        return new Config(enabled, activeProfile.equals(from) ? to : activeProfile, keys, next);
    }

    /** Adds an imported profile under a free name and selects it. An existing profile is never replaced. */
    public Config withImportedProfile(Profile p) {
        String name = freeProfileName(normalizeName(p.name()));
        return withProfile(p.renamed(name)).withActiveProfile(name);
    }

    /** No-op for a built-in profile. */
    public Config withoutProfile(String name) {
        if (isBuiltIn(name)) return this;
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
        return freeName(profiles, base);
    }

    private static String freeName(Map<String, Profile> taken, String base) {
        if (!taken.containsKey(base)) return base;
        for (int i = 2; ; i++) if (!taken.containsKey(base + "-" + i)) return base + "-" + i;
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
