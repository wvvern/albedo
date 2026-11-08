package dev.blockpainter.core.config;

import dev.blockpainter.core.Category;
import dev.blockpainter.core.Colors;

import java.util.ArrayList;
import java.util.Arrays;
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
 * A group writes only the look fields that differ from its category; a group with none takes the category's look.
 * Unknown keys are errors, so a typo is reported instead of silently ignored.
 */
public final class ConfigCodec {
    private static final List<String> LOOK_KEYS = Arrays.asList("mode", "color", "texture", "alpha");
    private static final List<String> ROOT_KEYS = Arrays.asList("enabled", "activeProfile", "keys", "profiles");
    private static final List<String> KEY_KEYS = Arrays.asList("toggle", "cycleProfile", "settings");
    private static final List<String> CATEGORY_KEYS = plus(LOOK_KEYS, "removeTint", "onlyGroups", "groups");
    private static final List<String> GROUP_KEYS = plus(LOOK_KEYS, "name", "match");

    private ConfigCodec() {}

    // ---------------------------------------------------------------- decode

    public static Config decode(String json) {
        Map<String, Object> root = object(Json.parse(json), "root", ROOT_KEYS);
        Map<String, Object> keys = root.containsKey("keys") ? object(root.get("keys"), "keys", KEY_KEYS) : Collections.<String, Object>emptyMap();
        Config.KeyBinds d = Config.KeyBinds.DEFAULT;
        Config.KeyBinds binds = new Config.KeyBinds(str(keys, "toggle", d.toggle), str(keys, "cycleProfile", d.cycleProfile),
                str(keys, "settings", d.settings));

        Map<String, Profile> profiles = new LinkedHashMap<String, Profile>();
        Object rawProfiles = root.get("profiles");
        if (rawProfiles != null) {
            for (Map.Entry<String, Object> e : object(rawProfiles, "profiles", null).entrySet()) {
                profiles.put(e.getKey(), decodeProfile(e.getKey(), object(e.getValue(), "profiles." + e.getKey(), null)));
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
            String where = name + "." + e.getKey();
            cats.put(c, decodeCategory(c, where, object(e.getValue(), where, CATEGORY_KEYS)));
        }
        return new Profile(name, cats, on);
    }

    static CategorySettings decodeCategory(Category category, String where, Map<String, Object> map) {
        Appearance appearance = decodeAppearance(category, where, map, Appearance.ORIGINAL);
        boolean onlyGroups = bool(map, "onlyGroups", false);
        List<Rule> rules = new ArrayList<Rule>();
        Object raw = map.get("groups");
        if (raw != null) {
            if (!(raw instanceof List)) throw new IllegalArgumentException(where + ".groups must be a list");
            int k = 0;
            for (Object o : (List<?>) raw) {
                String at = where + ".groups[" + k++ + "]";
                Map<String, Object> rm = object(o, at, GROUP_KEYS);
                Object match = rm.get("match");
                List<String> patterns = new ArrayList<String>();
                if (match instanceof String) patterns.add((String) match);
                else if (match instanceof List) for (Object p : (List<?>) match) patterns.add(String.valueOf(p));
                else throw new IllegalArgumentException(at + ": set \"match\" to \"pattern\" or [\"p1\", \"p2\"]");
                Rule r = new Rule(str(rm, "name", ""), patterns, decodeAppearance(category, at, rm, appearance), false);
                // No look fields means "the category's look": a live link under "only groups", a plain copy otherwise.
                rules.add(onlyGroups && Collections.disjoint(rm.keySet(), LOOK_KEYS) ? r.withGlobalLook() : r);
            }
        }
        return new CategorySettings(appearance, bool(map, "removeTint", false), onlyGroups, rules);
    }

    /** Unspecified fields inherit from {@code parent} (the category's look for groups). */
    static Appearance decodeAppearance(Category category, String where, Map<String, Object> map, Appearance parent) {
        try {
            Appearance.Mode mode = map.containsKey("mode") ? Appearance.Mode.byKey(str(map, "mode", "")) : parent.mode();
            if (!category.supportedModes().contains(mode)) {
                throw new IllegalArgumentException("this category cannot use mode '" + mode.key() + "'. Use one of " + keys(category.supportedModes()));
            }
            int color = map.containsKey("color") ? Colors.parse(str(map, "color", "")) : parent.color();
            String texture = map.containsKey("texture") ? str(map, "texture", "") : parent.texture();
            Appearance.Alpha alpha = map.containsKey("alpha") ? Appearance.Alpha.byKey(str(map, "alpha", "")) : parent.alpha();
            return new Appearance(mode, color, texture == null || texture.isEmpty() ? null : texture, alpha);
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
            encodeAppearance(m, cs.appearance(), Appearance.ORIGINAL);
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
                        // No look field means "the category's look", so an own look equal to it is written out.
                        if (Collections.disjoint(rm.keySet(), LOOK_KEYS)) rm.put("mode", r.appearance().mode().key());
                    }
                    rules.add(rm);
                }
                m.put("groups", rules);
            }
            out.put(e.getKey().key(), m);
        }
        return out;
    }

    /** Writes every field that differs from {@code base}; a missing texture against one that has it is written as "". */
    private static void encodeAppearance(Map<String, Object> m, Appearance a, Appearance base) {
        if (a.mode() != base.mode()) m.put("mode", a.mode().key());
        if (a.color() != base.color()) m.put("color", Colors.format(a.color()));
        if (a.texture() == null ? base.texture() != null : !a.texture().equals(base.texture())) {
            m.put("texture", a.texture() == null ? "" : a.texture());
        }
        if (a.alpha() != base.alpha()) m.put("alpha", a.alpha().key());
    }

    // ---------------------------------------------------------------- helpers

    /** A JSON object; with {@code known}, any other key is an error naming the allowed ones. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object o, String where, List<String> known) {
        if (!(o instanceof Map)) throw new IllegalArgumentException("'" + where + "' must be a JSON object");
        Map<String, Object> map = (Map<String, Object>) o;
        if (known != null) {
            for (String key : map.keySet()) {
                if (!known.contains(key)) throw new IllegalArgumentException(where + ": unknown setting '" + key + "'. Use one of " + known);
            }
        }
        return map;
    }

    private static List<String> plus(List<String> base, String... more) {
        List<String> out = new ArrayList<String>(base);
        out.addAll(Arrays.asList(more));
        return Collections.unmodifiableList(out);
    }

    private static List<String> keys(List<Appearance.Mode> modes) {
        List<String> out = new ArrayList<String>();
        for (Appearance.Mode m : modes) out.add(m.key());
        return out;
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
