package dev.albedo.core.config;

import dev.albedo.core.Category;
import dev.albedo.core.Colors;
import dev.albedo.core.Glob;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Converts {@link Config} to and from JSON such as the example below, where an unknown key is an error so that a
 * typo is reported instead of silently ignored:
 * <pre>
 * {
 *   "enabled": true,
 *   "activeProfile": "my-profile",
 *   "keys": { "toggle": "F6", "cycleProfile": "F7", "settings": "F9" },
 *   "servers": { "hypixel.net": "quake-black", "example.org": false },
 *   "profiles": {
 *     "my-profile": {
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
    private static final List<String> RECOLOR_KEYS = Arrays.asList("detail", "keepColors", "keepLight");
    private static final List<String> EDGE_KEYS = Arrays.asList("bevel", "outline");
    private static final List<String> LOOK_KEYS = Arrays.asList("mode", "color", "alpha", "detail", "keepColors", "keepLight",
            "bevel", "outline", "chroma");
    /** The JSON key of each look field, in the order of {@link Appearance.Field}. */
    private static final Map<String, Appearance.Field> FIELD_KEYS = new LinkedHashMap<String, Appearance.Field>();

    static {
        Appearance.Field[] fields = Appearance.Field.values();
        for (int i = 0; i < fields.length; i++) FIELD_KEYS.put(LOOK_KEYS.get(i), fields[i]);
    }
    private static final List<String> ROOT_KEYS = Arrays.asList("enabled", "activeProfile", "keys", "servers", "profiles");
    private static final List<String> KEY_KEYS = Arrays.asList("toggle", "cycleProfile", "settings");
    private static final List<String> CATEGORY_KEYS = plus(LOOK_KEYS, "removeTint", "onlyGroups", "groups");
    private static final List<String> GROUP_KEYS = plus(LOOK_KEYS, "name", "match");
    /** The first key of a profile file; its value is the file format version. */
    private static final String PROFILE_FILE = "albedoProfile";
    private static final int PROFILE_FILE_VERSION = 1;
    private static final List<String> PROFILE_FILE_KEYS = Arrays.asList(PROFILE_FILE, "name", "profile");

    private ConfigCodec() {}

    /**
     * One profile as a file to share, {@code {"albedoProfile": 1, "name": "my-profile", "profile": {...}}}, where the
     * "profile" object has the same keys as a profile in the config file.
     */
    public static String encodeProfileFile(Profile p) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put(PROFILE_FILE, PROFILE_FILE_VERSION);
        root.put("name", p.name());
        root.put("profile", encodeProfile(p));
        return Json.write(root);
    }

    /** Reads a profile file and normalizes its name, which the caller then makes unique. */
    public static Profile decodeProfileFile(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map) || !((Map<?, ?>) parsed).containsKey(PROFILE_FILE)) {
            throw new IllegalArgumentException("this is not an Albedo profile (no \"" + PROFILE_FILE + "\" key)");
        }
        Map<String, Object> root = object(parsed, "profile file", PROFILE_FILE_KEYS);
        Object version = root.get(PROFILE_FILE);
        if (!(version instanceof Number) || ((Number) version).doubleValue() != PROFILE_FILE_VERSION) {
            throw new IllegalArgumentException("profile file version " + version + " is not supported. Use version "
                    + PROFILE_FILE_VERSION);
        }
        String name = Config.normalizeName(str(root, "name", ""));
        if (name.isEmpty()) throw new IllegalArgumentException("the profile file has no \"name\"");
        Object profile = root.get("profile");
        return decodeProfile(name, profile == null ? Collections.<String, Object>emptyMap() : object(profile, "profile", null));
    }

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
        Set<String> profileNames = new LinkedHashSet<String>(Presets.all().keySet());
        profileNames.addAll(profiles.keySet());
        List<ServerRule> servers = new ArrayList<ServerRule>();
        Object rawServers = root.get("servers");
        if (rawServers != null) {
            Map<String, String> written = new LinkedHashMap<String, String>();
            for (Map.Entry<String, Object> e : object(rawServers, "servers", null).entrySet()) {
                ServerRule r = decodeServer(e.getKey(), e.getValue(), profileNames);
                String first = written.put(r.server(), e.getKey());
                if (first != null) throw new IllegalArgumentException("servers." + e.getKey() + ": the same server as \"" + first + "\"");
                servers.add(r);
            }
        }
        return new Config(bool(root, "enabled", true), str(root, "activeProfile", ""), binds, profiles, servers);
    }

    /** A rule such as {@code "hypixel.net": "quake-black"}, where {@code false} turns the mod off on the server. */
    private static ServerRule decodeServer(String server, Object value, Set<String> profiles) {
        String where = "servers." + server;
        if (ServerRule.host(server).isEmpty()) throw new IllegalArgumentException(where + ": the server name is empty");
        if (Boolean.FALSE.equals(value)) return new ServerRule(server, null);
        if (!(value instanceof String) || !profiles.contains(value)) {
            throw new IllegalArgumentException(where + ": set a profile name (" + profiles + ") or false");
        }
        return new ServerRule(server, (String) value);
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
                for (String p : patterns) {
                    String problem = Glob.problem(p);
                    if (problem != null) throw new IllegalArgumentException(at + ": \"" + p + "\" is not a valid pattern: " + problem);
                }
                EnumSet<Appearance.Field> own = EnumSet.noneOf(Appearance.Field.class);
                for (Map.Entry<String, Appearance.Field> f : FIELD_KEYS.entrySet()) if (rm.containsKey(f.getKey())) own.add(f.getValue());
                // No look fields means "the category's look": a live link under "only groups", a plain copy otherwise.
                if (own.isEmpty() && !onlyGroups) own = EnumSet.allOf(Appearance.Field.class);
                rules.add(new Rule(str(rm, "name", ""), patterns, decodeAppearance(category, at, rm, appearance), own));
            }
        }
        return new CategorySettings(appearance, bool(map, "removeTint", false), onlyGroups, rules);
    }

    /** Unspecified fields inherit from {@code parent} (the category's look for groups). */
    static Appearance decodeAppearance(Category category, String where, Map<String, Object> map, Appearance parent) {
        try {
            Appearance.Mode mode = map.containsKey("mode") ? Appearance.Mode.byKey(str(map, "mode", "")) : parent.mode();
            if (!category.supportedModes().contains(mode)) {
                throw new IllegalArgumentException("this category cannot use mode '" + mode.key() + "'. Use one of "
                        + keys(category.supportedModes()));
            }
            int color = map.containsKey("color") ? Colors.parse(str(map, "color", "")) : parent.color();
            Appearance.Alpha alpha = map.containsKey("alpha") ? Appearance.Alpha.byKey(str(map, "alpha", "")) : parent.alpha();
            if (!category.supportedModes().contains(Appearance.Mode.RECOLOR) && !Collections.disjoint(map.keySet(), RECOLOR_KEYS)) {
                throw new IllegalArgumentException("this category has no recolor style, so " + RECOLOR_KEYS + " have no meaning here");
            }
            if (!category.hasEdgeEffects() && !Collections.disjoint(map.keySet(), EDGE_KEYS)) {
                throw new IllegalArgumentException(EDGE_KEYS + " work only on blocks and fluids");
            }
            if (!category.hasChroma() && map.containsKey("chroma")) {
                throw new IllegalArgumentException("this category has no recolor style, so 'chroma' has no meaning here");
            }
            return new Appearance(mode, color, alpha,
                    percent(map, "detail", parent.detail()), percent(map, "keepColors", parent.keepColors()),
                    percent(map, "keepLight", parent.keepLight()), percent(map, "bevel", parent.bevel()),
                    percent(map, "outline", parent.outline()), percent(map, "chroma", parent.chroma()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
        }
    }

    public static String encode(Config config) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("enabled", config.enabled());
        root.put("activeProfile", config.activeProfileName());
        Map<String, Object> keys = new LinkedHashMap<String, Object>();
        keys.put("toggle", config.keys().toggle);
        keys.put("cycleProfile", config.keys().cycleProfile);
        keys.put("settings", config.keys().settings);
        root.put("keys", keys);
        // Only when there are rules, so that an older version of the mod can still read the file.
        if (!config.servers().isEmpty()) {
            Map<String, Object> servers = new LinkedHashMap<String, Object>();
            for (ServerRule r : config.servers().values()) servers.put(r.server(), r.profile() == null ? (Object) false : r.profile());
            root.put("servers", servers);
        }
        Map<String, Object> profiles = new LinkedHashMap<String, Object>();
        // Built-in profiles come from the code, so the file holds only the user's own.
        for (Profile p : config.userProfiles().values()) profiles.put(p.name(), encodeProfile(p));
        root.put("profiles", profiles);
        return Json.write(root);
    }

    static Map<String, Object> encodeProfile(Profile p) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Toggle t : Toggle.values()) if (p.is(t) != t.defaultOn) out.put(t.key, p.is(t));
        for (Map.Entry<Category, CategorySettings> e : p.configured().entrySet()) {
            CategorySettings cs = e.getValue();
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            // Recolor and effect fields only where a look uses them, so that an older version can still read the file.
            boolean recolor = uses(cs, a -> a.mode() == Appearance.Mode.RECOLOR);
            boolean effects = uses(cs, Appearance::hasEffects);
            Appearance written = unused(cs.appearance(), Appearance.ORIGINAL, recolor, effects);
            encodeAppearance(m, written, Appearance.ORIGINAL);
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
                        Appearance look = r.lookIn(written);
                        // A group without effects still writes the 0 that turns off an effect of its category.
                        encodeAppearance(rm, unused(look, written, look.mode() == Appearance.Mode.RECOLOR, effects), written);
                        // No look field means "the category's look", so an own look equal to it is written out.
                        if (Collections.disjoint(rm.keySet(), LOOK_KEYS)) rm.put("mode", look.mode().key());
                    }
                    rules.add(rm);
                }
                m.put("groups", rules);
            }
            out.put(e.getKey().key(), m);
        }
        return out;
    }

    /** Writes every field that differs from {@code base}. */
    private static void encodeAppearance(Map<String, Object> m, Appearance a, Appearance base) {
        if (a.mode() != base.mode()) m.put("mode", a.mode().key());
        if (a.color() != base.color()) m.put("color", Colors.format(a.color()));
        if (a.alpha() != base.alpha()) m.put("alpha", a.alpha().key());
        if (a.detail() != base.detail()) m.put("detail", a.detail());
        if (a.keepColors() != base.keepColors()) m.put("keepColors", a.keepColors());
        if (a.keepLight() != base.keepLight()) m.put("keepLight", a.keepLight());
        if (a.bevel() != base.bevel()) m.put("bevel", a.bevel());
        if (a.outline() != base.outline()) m.put("outline", a.outline());
        if (a.chroma() != base.chroma()) m.put("chroma", a.chroma());
    }

    /** Whether the category look or the look of a group matches. */
    private static boolean uses(CategorySettings cs, Predicate<Appearance> test) {
        if (test.test(cs.appearance())) return true;
        for (Rule r : cs.rules()) if (test.test(cs.lookOf(r))) return true;
        return false;
    }

    /** {@code a} with the recolor or effect fields of {@code from} where they are not used, so that they are not written. */
    private static Appearance unused(Appearance a, Appearance from, boolean recolor, boolean effects) {
        Appearance out = recolor ? a : a.withRecolor(from.detail(), from.keepColors(), from.keepLight());
        return effects ? out : out.withEffects(from.bevel(), from.outline(), from.chroma());
    }

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

    private static int percent(Map<String, Object> m, String key, int def) {
        Object o = m.get(key);
        if (o == null) return def;
        double v = o instanceof Number ? ((Number) o).doubleValue() : Double.NaN;
        if (v != Math.rint(v) || v < 0 || v > 100) throw new IllegalArgumentException("'" + key + "' must be a whole number from 0 to 100");
        return (int) v;
    }

    private static String str(Map<String, Object> m, String key, String def) {
        Object o = m.get(key);
        if (o == null) return def;
        if (!(o instanceof String)) throw new IllegalArgumentException("'" + key + "' must be a string");
        return (String) o;
    }
}
