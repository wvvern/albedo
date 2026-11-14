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
import java.util.stream.Collectors;

import static dev.albedo.core.config.JsonFields.keys;
import static dev.albedo.core.config.JsonFields.object;

/**
 * Converts {@link Config} to and from JSON such as the example below, where an unknown key is an error so that a
 * typo is reported instead of silently ignored, and where the encoder leaves out each key at its default or without
 * meaning, so that an older version of the mod can still read the file:
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
    /** The Chroma options, which have a meaning only when the resolved {@code chroma} is above 0. */
    private static final List<String> CHROMA_KEYS = Arrays.asList("chromaSpeed", "chromaSaturation", "chromaBrightness", "chromaEdge");
    /** The JSON key of each look field in the order of {@link Appearance.Field}, then the Chroma options. */
    private static final List<String> LOOK_KEYS = plus(Arrays.asList("mode", "color", "alpha", "detail", "keepColors", "keepLight",
            "bevel", "outline", "chroma"), CHROMA_KEYS.toArray(new String[0]));
    /** The look field that each JSON key sets. */
    private static final Map<String, Appearance.Field> FIELD_KEYS = new LinkedHashMap<String, Appearance.Field>();

    static {
        Appearance.Field[] fields = Appearance.Field.values();
        for (int i = 0; i < fields.length; i++) FIELD_KEYS.put(LOOK_KEYS.get(i), fields[i]);
        for (String key : CHROMA_KEYS) FIELD_KEYS.put(key, Appearance.Field.CHROMA);
    }
    private static final List<String> ROOT_KEYS = Arrays.asList("enabled", "activeProfile", "keys", "servers", "profiles");
    private static final List<String> KEY_KEYS = Collections.unmodifiableList(
            Arrays.stream(Config.KeyBinds.Action.values()).map(a -> a.key).collect(Collectors.toList()));
    private static final List<String> CATEGORY_KEYS = plus(LOOK_KEYS, "removeTint", "onlyGroups", "groups");
    private static final List<String> SKY_CATEGORY_KEYS = plus(CATEGORY_KEYS, SkyCodec.KEYS.toArray(new String[0]));
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
        Config.KeyBinds binds = Config.KeyBinds.of(a -> str(keys, a.key, Config.KeyBinds.DEFAULT.get(a)));

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

    private static Profile decodeProfile(String name, Map<String, Object> map) {
        Map<Category, CategorySettings> cats = new EnumMap<Category, CategorySettings>(Category.class);
        Set<Toggle> on = EnumSet.noneOf(Toggle.class);
        for (Toggle t : Toggle.values()) if (bool(map, t.key, t.defaultOn)) on.add(t);
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (Toggle.byKey(e.getKey()) != null) continue;
            Category c = Category.byKey(e.getKey());
            if (c == null) throw new IllegalArgumentException("profile '" + name + "': unknown setting '" + e.getKey() + "'");
            String where = name + "." + e.getKey();
            cats.put(c, decodeCategory(c, where, object(e.getValue(), where, c == Category.SKY ? SKY_CATEGORY_KEYS : CATEGORY_KEYS)));
        }
        return new Profile(name, cats, on);
    }

    private static CategorySettings decodeCategory(Category category, String where, Map<String, Object> map) {
        Appearance appearance = decodeAppearance(category, where, map, Appearance.ORIGINAL);
        boolean onlyGroups = bool(map, "onlyGroups", false);
        List<Rule> rules = new ArrayList<Rule>();
        Object raw = map.get("groups");
        if (raw != null) {
            if (!(raw instanceof List)) throw new IllegalArgumentException(where + ".groups: use a list");
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
                Appearance look = decodeAppearance(category, at, rm, appearance);
                // Chroma options left in a group whose Chroma is off are ignored, so they do not make Chroma its own field.
                if (!rm.containsKey("chroma") && !look.chroma().isOn()) own.remove(Appearance.Field.CHROMA);
                // No look fields means "the category's look": a live link under "only groups", a plain copy otherwise.
                if (own.isEmpty() && !onlyGroups) own = EnumSet.allOf(Appearance.Field.class);
                rules.add(new Rule(str(rm, "name", ""), patterns, look, own));
            }
        }
        SkyDesign sky = category == Category.SKY ? SkyCodec.decode(where, map, appearance.mode() == Appearance.Mode.LAYERS) : SkyDesign.NONE;
        return new CategorySettings(appearance, bool(map, "removeTint", false), onlyGroups, rules, sky);
    }

    /** Unspecified fields inherit from {@code parent} (the category's look for groups). */
    private static Appearance decodeAppearance(Category category, String where, Map<String, Object> map, Appearance parent) {
        try {
            Appearance.Mode mode = choice(map, "mode", parent.mode());
            if (!category.supportedModes().contains(mode)) {
                throw new IllegalArgumentException("this category cannot use mode '" + mode.key() + "'. Use one of "
                        + keys(category.supportedModes()));
            }
            int color = map.containsKey("color") ? Colors.parse(str(map, "color", "")) : parent.color();
            Appearance.Alpha alpha = choice(map, "alpha", parent.alpha());
            boolean recolor = category.supportedModes().contains(Appearance.Mode.RECOLOR);
            if (!recolor && !Collections.disjoint(map.keySet(), RECOLOR_KEYS)) {
                throw new IllegalArgumentException("this category has no recolor style, so " + RECOLOR_KEYS + " have no meaning here");
            }
            if (!category.hasBevel() && map.containsKey("bevel")) {
                throw new IllegalArgumentException("'bevel' works only on blocks and fluids");
            }
            if (!category.hasOutline() && map.containsKey("outline")) {
                throw new IllegalArgumentException("'outline' works only on blocks, fluids and items");
            }
            if (!category.hasOutline() && map.containsKey("chromaEdge")) {
                throw new IllegalArgumentException("'chromaEdge' works only on blocks, fluids and items");
            }
            if (!recolor && map.containsKey("chroma")) {
                throw new IllegalArgumentException("this category has no recolor style, so 'chroma' has no meaning here");
            }
            if (!recolor && !Collections.disjoint(map.keySet(), CHROMA_KEYS)) {
                throw new IllegalArgumentException("this category has no recolor style, so " + CHROMA_KEYS + " have no meaning here");
            }
            // Mobs and players cannot have Chroma; older files can, so drop it.
            Chroma chroma = category.hasChroma() ? decodeChroma(map, parent.chroma()) : Chroma.OFF;
            return new Appearance(mode, color, alpha,
                    percent(map, "detail", parent.detail()), percent(map, "keepColors", parent.keepColors()),
                    percent(map, "keepLight", parent.keepLight()), percent(map, "bevel", parent.bevel()),
                    percent(map, "outline", parent.outline()), chroma);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
        }
    }

    /** The Chroma of a look, where a missing key inherits from {@code parent} and the options are ignored when Chroma is off. */
    private static Chroma decodeChroma(Map<String, Object> map, Chroma parent) {
        Chroma out = parent.withStrength(percent(map, "chroma", parent.strength()));
        // A hand edit that turns Chroma off can leave the options in the file, which must not make the whole file invalid.
        if (!out.isOn()) return out;
        return out.withOptions(percent(map, "chromaSpeed", parent.speed()), percent(map, "chromaSaturation", parent.saturation()),
                percent(map, "chromaBrightness", parent.brightness())).withEdge(bool(map, "chromaEdge", parent.edge()));
    }

    public static String encode(Config config) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("enabled", config.enabled());
        root.put("activeProfile", config.activeProfileName());
        Map<String, Object> keys = new LinkedHashMap<String, Object>();
        for (Config.KeyBinds.Action a : Config.KeyBinds.Action.values()) keys.put(a.key, config.keys().get(a));
        root.put("keys", keys);
        if (!config.servers().isEmpty()) {
            Map<String, Object> servers = new LinkedHashMap<String, Object>();
            for (ServerRule r : config.servers().values()) servers.put(r.server(), r.profile() == null ? (Object) false : r.profile());
            root.put("servers", servers);
        }
        Map<String, Object> profiles = new LinkedHashMap<String, Object>();
        for (Profile p : config.userProfiles().values()) profiles.put(p.name(), encodeProfile(p));
        root.put("profiles", profiles);
        return Json.write(root);
    }

    private static Map<String, Object> encodeProfile(Profile p) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Toggle t : Toggle.values()) if (p.is(t) != t.defaultOn) out.put(t.key, p.is(t));
        for (Map.Entry<Category, CategorySettings> e : p.configured().entrySet()) {
            CategorySettings cs = e.getValue();
            boolean layers = cs.appearance().mode() == Appearance.Mode.LAYERS;
            if (!layers && cs.withSky(SkyDesign.NONE).equals(CategorySettings.UNTOUCHED)) continue;
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            boolean recolor = uses(cs, a -> a.mode() == Appearance.Mode.RECOLOR);
            boolean effects = uses(cs, Appearance::hasEffects);
            Appearance written = unused(cs.appearance(), Appearance.ORIGINAL, recolor, effects);
            encodeAppearance(m, written, Appearance.ORIGINAL);
            if (layers) SkyCodec.encode(m, cs.sky());
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
        Chroma c = a.chroma(), b = base.chroma();
        if (c.strength() != b.strength()) m.put("chroma", c.strength());
        if (!c.isOn()) return;
        if (c.speed() != b.speed()) m.put("chromaSpeed", c.speed());
        if (c.saturation() != b.saturation()) m.put("chromaSaturation", c.saturation());
        if (c.brightness() != b.brightness()) m.put("chromaBrightness", c.brightness());
        if (c.edge() != b.edge()) m.put("chromaEdge", c.edge());
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
        if (!effects) return out.withEffects(from.bevel(), from.outline(), from.chroma());
        // The Chroma options are not written while Chroma is off, so a read gives them from the parent.
        return out.chroma().isOn() ? out : out.withEffects(out.bevel(), out.outline(), from.chroma().withStrength(0));
    }

    private static List<String> plus(List<String> base, String... more) {
        List<String> out = new ArrayList<String>(base);
        out.addAll(Arrays.asList(more));
        return Collections.unmodifiableList(out);
    }

    /** The place of a key in an error, which {@link #decodeAppearance} prefixes with the place of the look. */
    private static String place(String key) {
        return "'" + key + "'";
    }

    /** In the config file, a JSON null value counts as a missing key, except for a choice. */
    private static boolean missing(Map<String, Object> m, String key) {
        return m.get(key) == null;
    }

    private static <E extends Enum<E>> E choice(Map<String, Object> m, String key, E def) {
        return JsonFields.choice(m, key, def, place(key));
    }

    private static boolean bool(Map<String, Object> m, String key, boolean def) {
        return missing(m, key) ? def : JsonFields.bool(m, key, place(key));
    }

    private static int percent(Map<String, Object> m, String key, int def) {
        return missing(m, key) ? def : JsonFields.number(m, key, 0, 100, place(key));
    }

    private static String str(Map<String, Object> m, String key, String def) {
        return missing(m, key) ? def : JsonFields.string(m, key, place(key));
    }
}
