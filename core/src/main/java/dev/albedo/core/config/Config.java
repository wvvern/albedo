package dev.albedo.core.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The whole user configuration, immutable so that each edit produces a new instance. */
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

    /** Whether the mod is on and which profile is active. */
    public static final class State {
        public final boolean enabled;
        public final String profile;

        public State(boolean enabled, String profile) {
            this.enabled = enabled;
            this.profile = profile;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof State && ((State) o).enabled == enabled && ((State) o).profile.equals(profile);
        }

        @Override
        public int hashCode() { return profile.hashCode() * 2 + (enabled ? 1 : 0); }

        @Override
        public String toString() { return enabled ? profile : "off"; }
    }

    private final boolean enabled;
    private final String activeProfile;
    private final KeyBinds keys;
    private final Map<String, Profile> profiles;
    private final Map<String, ServerRule> servers;

    public Config(boolean enabled, String activeProfile, KeyBinds keys, Map<String, Profile> profiles) {
        this(enabled, activeProfile, keys, profiles, Collections.<ServerRule>emptyList());
    }

    /**
     * A profile with a built-in name that differs from the built-in one is kept as a new profile
     * ("quake-white-custom"), which becomes active if the built-in one was and takes its server rules.
     *
     * @param servers server rules, where the last rule of a server wins and a rule for an unknown profile is dropped
     */
    public Config(boolean enabled, String activeProfile, KeyBinds keys, Map<String, Profile> profiles, Collection<ServerRule> servers) {
        Map<String, Profile> all = new LinkedHashMap<String, Profile>(Presets.all());
        for (Profile p : profiles.values()) if (!isBuiltIn(p.name())) all.put(p.name(), p);
        // Changed built-in profiles last, so that their new names do not take a name that a user profile has.
        String active = activeProfile;
        Collection<ServerRule> rules = servers;
        for (Profile p : profiles.values()) {
            if (isBuiltIn(p.name()) && !p.equals(Presets.all().get(p.name()))) {
                String copy = freeName(all, p.name() + "-custom");
                all.put(copy, p.renamed(copy));
                if (p.name().equals(active)) active = copy;
                rules = moved(rules, p.name(), copy);
            }
        }
        this.enabled = enabled;
        this.keys = keys;
        this.profiles = Collections.unmodifiableMap(all);
        this.activeProfile = all.containsKey(active) ? active : all.keySet().iterator().next();
        Map<String, ServerRule> byServer = new LinkedHashMap<String, ServerRule>();
        for (ServerRule r : rules) {
            if (!r.server().isEmpty() && (r.profile() == null || all.containsKey(r.profile()))) byServer.put(r.server(), r);
        }
        this.servers = Collections.unmodifiableMap(byServer);
    }

    public static Config defaults() {
        return new Config(true, "quake-white", KeyBinds.DEFAULT, Collections.<String, Profile>emptyMap());
    }

    public static boolean isBuiltIn(String name) {
        return Presets.all().containsKey(name);
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
    /** The server rules by server name, in the order the player added them. */
    public Map<String, ServerRule> servers() { return servers; }
    public State state() { return new State(enabled, activeProfile); }

    public Config withEnabled(boolean value) {
        return withState(new State(value, activeProfile));
    }

    public Config withActiveProfile(String name) {
        return withState(new State(enabled, name));
    }

    /** The same config with another on or off state and active profile, where an unknown profile selects the first one. */
    public Config withState(State s) {
        return new Config(s.enabled, s.profile, keys, profiles, servers.values());
    }

    /** Replaces the profile with the same name, or appends it. */
    public Config withProfile(Profile p) {
        Map<String, Profile> next = new LinkedHashMap<String, Profile>(profiles);
        next.put(p.name(), p);
        return new Config(enabled, activeProfile, keys, next, servers.values());
    }

    /** The rule for a server address, where the longest matching server name wins, or null when no rule matches. */
    public ServerRule serverRule(String address) {
        String host = ServerRule.host(address);
        if (host.isEmpty()) return null;
        boolean ipAddress = ServerRule.isIpAddress(host);
        ServerRule best = null;
        for (ServerRule r : servers.values()) {
            if (r.matches(host, ipAddress) && (best == null || r.server().length() > best.server().length())) best = r;
        }
        return best;
    }

    /** Replaces the rule for the same server, or appends it. */
    public Config withServer(ServerRule rule) {
        Map<String, ServerRule> next = new LinkedHashMap<String, ServerRule>(servers);
        next.put(rule.server(), rule);
        return new Config(enabled, activeProfile, keys, profiles, next.values());
    }

    public Config withoutServer(String server) {
        Map<String, ServerRule> next = new LinkedHashMap<String, ServerRule>(servers);
        next.remove(ServerRule.host(server));
        return new Config(enabled, activeProfile, keys, profiles, next.values());
    }

    /** Profile names are lower-case words joined by "-", e.g. "my-profile". */
    public static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", "-").toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Renames in place, keeping the profile's position and active state, and adds a number to a name already in use
     * ("quake-white-2").
     */
    public Config withRenamedProfile(String from, String newName) {
        String clean = normalizeName(newName);
        if (!profiles.containsKey(from) || isBuiltIn(from) || clean.isEmpty() || clean.equals(from)) return this;
        String to = freeProfileName(clean);
        Map<String, Profile> next = new LinkedHashMap<String, Profile>();
        for (Profile p : profiles.values()) next.put(p.name().equals(from) ? to : p.name(), p.name().equals(from) ? p.renamed(to) : p);
        return new Config(enabled, activeProfile.equals(from) ? to : activeProfile, keys, next, moved(servers.values(), from, to));
    }

    private static List<ServerRule> moved(Collection<ServerRule> rules, String from, String to) {
        List<ServerRule> out = new ArrayList<ServerRule>();
        for (ServerRule r : rules) out.add(from.equals(r.profile()) ? r.withProfile(to) : r);
        return out;
    }

    /** Adds an imported profile under a free name, so it never replaces an existing profile, and selects it. */
    public Config withImportedProfile(Profile p) {
        String name = freeProfileName(normalizeName(p.name()));
        return withProfile(p.renamed(name)).withActiveProfile(name);
    }

    /** Deletes a user profile with its server rules, and does nothing for a built-in profile. */
    public Config withoutProfile(String name) {
        if (isBuiltIn(name)) return this;
        Map<String, Profile> next = new LinkedHashMap<String, Profile>(profiles);
        next.remove(name);
        return new Config(enabled, activeProfile, keys, next, servers.values());
    }

    /** The profile after the active one, wrapping around ({@code step} may be negative). */
    public String profileNameAt(int step) {
        List<String> names = new ArrayList<String>(profiles.keySet());
        int i = names.indexOf(activeProfile);
        return names.get(((i + step) % names.size() + names.size()) % names.size());
    }

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
                && c.profiles.equals(profiles) && c.servers.equals(servers);
    }

    @Override
    public int hashCode() { return profiles.hashCode() * 31 + activeProfile.hashCode(); }
}
