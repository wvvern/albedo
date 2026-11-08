package dev.singlecolor.core.config;

import dev.singlecolor.core.Category;
import dev.singlecolor.core.Colors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts {@link Config} to and from JSON. Example:
 * <pre>
 * {
 *   "enabled": true,
 *   "activeProfile": "quake-white",
 *   "keys": { "toggle": "F6", "cycleProfile": "F7", "settings": "F9" },
 *   "profiles": {
 *     "quake-white": {
 *       "fullbright": true, "shading": true, "foliage": false,
 *       "blocks": { "mode": "solid", "color": "#FFFFFF", "removeTint": true,
 *                   "groups": [ { "name": "Ground", "match": ["dirt", "grass_block*", "gravel"], "color": "#7A5C3A" } ] },
 *       "sky":    { "mode": "solid", "color": "#FFFFFF" }
 *     }
 *   }
 * }
 * </pre>
 */
public final class ConfigCodec {
    private ConfigCodec() {}

    // ---------------------------------------------------------------- decode

    public static Config decode(String json) {
        Map<String, Object> root = asMap(Json.parse(json), "root");
        Map<String, Object> keys = root.containsKey("keys") ? asMap(root.get("keys"), "keys") : Collections.<String, Object>emptyMap();
        Config.KeyBinds d = Config.KeyBinds.DEFAULT;
        Config.KeyBinds binds = new Config.KeyBinds(str(keys, "toggle", d.toggle), str(keys, "cycleProfile", d.cycleProfile),
                str(keys, "settings", d.settings));

        Map<String, Profile> profiles = new LinkedHashMap<String, Profile>();
        Object rawProfiles = root.get("profiles");
        if (rawProfiles != null) {
            for (Map.Entry<String, Object> e : asMap(rawProfiles, "profiles").entrySet()) {
                profiles.put(e.getKey(), decodeProfile(e.getKey(), asMap(e.getValue(), "profiles." + e.getKey())));
            }
        }
        if (profiles.isEmpty()) profiles.putAll(Presets.all());
        return new Config(bool(root, "enabled", true), str(root, "activeProfile", ""), binds, profiles);
    }

    static Profile decodeProfile(String name, Map<String, Object> map) {
        Map<Category, CategorySettings> cats = new EnumMap<Category, CategorySettings>(Category.class);
        Set<Toggle> on = EnumSet.noneOf(Toggle.class);
        for (Toggle t : Toggle.values()) if (bool(map, t.key, t.defaultOn)) on.add(t);
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (Toggle.byKey(e.getKey()) != null) continue;
            Category c = Category.byKey(e.getKey());
            if (c == null) throw new IllegalArgumentException("profile '" + name + "': unknown setting '" + e.getKey() + "'");
            cats.put(c, decodeCategory(name + "." + e.getKey(), asMap(e.getValue(), e.getKey())));
        }
        return new Profile(name, cats, on);
    }

    private static final String[] LOOK_KEYS = {"mode", "color", "texture", "alpha"};

    static CategorySettings decodeCategory(String where, Map<String, Object> map) {
        Appearance appearance = decodeAppearance(where, map, Appearance.ORIGINAL);
        List<Rule> rules = new ArrayList<Rule>();
        Object raw = map.get("groups");
        if (raw != null) {
            if (!(raw instanceof List)) throw new IllegalArgumentException(where + ".groups must be a list");
            int k = 0;
            for (Object o : (List<?>) raw) {
                String at = where + ".groups[" + k++ + "]";
                Map<String, Object> rm = asMap(o, at);
                Object match = rm.get("match");
                List<String> patterns = new ArrayList<String>();
                if (match instanceof String) patterns.add((String) match);
                else if (match instanceof List) for (Object p : (List<?>) match) patterns.add(String.valueOf(p));
                else throw new IllegalArgumentException(at + " needs \"match\": \"pattern\" or [\"p1\", \"p2\"]");
                boolean global = true;
                for (String key : LOOK_KEYS) if (rm.containsKey(key)) global = false;
                Rule r = new Rule(str(rm, "name", ""), patterns, decodeAppearance(at, rm, appearance), false);
                rules.add(global ? r.withGlobalLook() : r);
            }
        }
        return new CategorySettings(appearance, bool(map, "removeTint", false), bool(map, "onlyGroups", false), rules);
    }

    /** Unspecified fields inherit from {@code parent} (category defaults for rules). */
    static Appearance decodeAppearance(String where, Map<String, Object> map, Appearance parent) {
        try {
            Appearance.Mode mode = map.containsKey("mode") ? Appearance.Mode.byKey(str(map, "mode", "")) : parent.mode();
            int color = map.containsKey("color") ? Colors.parse(str(map, "color", "")) : parent.color();
            String texture = map.containsKey("texture") ? str(map, "texture", null) : parent.texture();
            Appearance.Alpha alpha = map.containsKey("alpha") ? Appearance.Alpha.byKey(str(map, "alpha", "")) : parent.alpha();
            return new Appearance(mode, color, texture, alpha);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- encode

    public static String encode(Config config) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("enabled", config.enabled());
        root.put("activeProfile", config.activeProfileName());
        Map<String, Object> keys = new LinkedHashMap<String, Object>();
        keys.put("toggle", config.keys().toggle);
        keys.put("cycleProfile", config.keys().cycleProfile);
        keys.put("settings", config.keys().settings);
        root.put("keys", keys);
        Map<String, Object> profiles = new LinkedHashMap<String, Object>();
        for (Profile p : config.profiles().values()) profiles.put(p.name(), encodeProfile(p));
        root.put("profiles", profiles);
        return Json.write(root);
    }

    static Map<String, Object> encodeProfile(Profile p) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Toggle t : Toggle.values()) if (p.is(t) != t.defaultOn) out.put(t.key, p.is(t));
        for (Map.Entry<Category, CategorySettings> e : p.configured().entrySet()) {
            CategorySettings cs = e.getValue();
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            encodeAppearance(m, cs.appearance(), null);
            if (cs.removeTint()) m.put("removeTint", true);
            if (cs.onlyGroups()) m.put("onlyGroups", true);
            if (!cs.rules().isEmpty()) {
                List<Object> rules = new ArrayList<Object>();
                for (Rule r : cs.rules()) {
                    Map<String, Object> rm = new LinkedHashMap<String, Object>();
                    if (!r.name().isEmpty()) rm.put("name", r.name());
                    List<String> pats = r.patterns();
                    rm.put("match", pats.size() == 1 ? (Object) pats.get(0) : new ArrayList<Object>(pats));
                    if (!r.usesGlobalLook()) {
                        encodeAppearance(rm, r.appearance(), cs.appearance());
                        // No look field means "global look", so an own look equal to the global one is written out.
                        if (!rm.containsKey("mode") && !rm.containsKey("color") && !rm.containsKey("texture") && !rm.containsKey("alpha")) {
                            rm.put("mode", r.appearance().mode().key());
                        }
                    }
                    rules.add(rm);
                }
                m.put("groups", rules);
            }
            out.put(e.getKey().key(), m);
        }
        return out;
    }

    /** Writes only what differs from {@code parent} (or everything relevant when parent is null). */
    private static void encodeAppearance(Map<String, Object> m, Appearance a, Appearance parent) {
        if (parent == null || a.mode() != parent.mode()) m.put("mode", a.mode().key());
        boolean colorUsed = a.mode() == Appearance.Mode.SOLID || a.mode() == Appearance.Mode.TEXTURE;
        if (colorUsed && (parent == null || a.color() != parent.color())) m.put("color", Colors.format(a.color()));
        if (a.texture() != null && (parent == null || !a.texture().equals(parent.texture()))) m.put("texture", a.texture());
        if (a.alpha() != Appearance.Alpha.KEEP && (parent == null || a.alpha() != parent.alpha())) m.put("alpha", a.alpha().key());
    }

    // ---------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o, String where) {
        if (!(o instanceof Map)) throw new IllegalArgumentException("'" + where + "' must be a JSON object");
        return (Map<String, Object>) o;
    }

    private static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object o = m.get(key);
        if (o == null) return def;
        if (!(o instanceof Boolean)) throw new IllegalArgumentException("'" + key + "' must be true or false");
        return (Boolean) o;
    }

    private static String str(Map<String, Object> m, String key, String def) {
        Object o = m.get(key);
        if (o == null) return def;
        if (!(o instanceof String)) throw new IllegalArgumentException("'" + key + "' must be a string");
        return (String) o;
    }
}
