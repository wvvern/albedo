package dev.albedo.core.config;

import dev.albedo.core.Colors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads and writes the layers of a custom sky inside the sky category, for example
 * {@code "sky": {"mode": "layers", "celestial": "hide", "layers": [{"id": 1, "kind": "gradient", ...}]}}, where each
 * error names the place in the file.
 */
final class SkyCodec {
    /** The keys of the sky category that exist only with the style "layers". */
    static final List<String> KEYS = Arrays.asList("celestial", "light", "layers");
    private static final List<String> LAYER_KEYS = Arrays.asList("id", "kind", "blend", "opacity", "output", "mask", "color1", "color2",
            "amount", "height", "seed", "transform");
    private static final List<String> MASK_KEYS = Arrays.asList("layer", "mode");
    private static final List<String> TRANSFORM_KEYS = Arrays.asList("yaw", "pitch", "roll", "scale");

    private SkyCodec() {}

    /**
     * The custom sky of a sky category, or {@link SkyDesign#NONE} when the style is not "layers".
     *
     * @param where the place of the category in the file, such as "night.sky"
     */
    static SkyDesign decode(String where, Map<String, Object> category, boolean layersStyle) {
        if (!layersStyle) {
            for (String k : KEYS) {
                if (category.containsKey(k)) throw new IllegalArgumentException(where + ": '" + k + "' works only with \"mode\": \"layers\"");
            }
            return SkyDesign.NONE;
        }
        SkyDesign.Celestial celestial = SkyDesign.Celestial.SHOW;
        SkyDesign.Light light = SkyDesign.Light.FIXED;
        try {
            if (category.containsKey("celestial")) celestial = SkyDesign.Celestial.byKey(string(category, "celestial", where));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ".celestial: use one of " + keys(SkyDesign.Celestial.values()), e);
        }
        try {
            if (category.containsKey("light")) light = SkyDesign.Light.byKey(string(category, "light", where));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ".light: use one of " + keys(SkyDesign.Light.values()), e);
        }
        Object raw = category.get("layers");
        List<SkyLayer> layers = new ArrayList<SkyLayer>();
        if (raw != null) {
            if (!(raw instanceof List)) throw new IllegalArgumentException(where + ".layers must be a list");
            List<?> list = (List<?>) raw;
            if (list.size() > SkyDesign.MAX_LAYERS) {
                throw new IllegalArgumentException(where + ".layers: a sky has a maximum of " + SkyDesign.MAX_LAYERS + " layers");
            }
            Set<Integer> ids = new HashSet<Integer>();
            for (int i = 0; i < list.size(); i++) {
                String at = where + ".layers[" + i + "]";
                SkyLayer layer = layer(at, object(list.get(i), at, LAYER_KEYS), ids);
                ids.add(layer.id());
                layers.add(layer);
            }
        }
        return new SkyDesign(layers, celestial, light);
    }

    /** One layer, whose mask may name only an id in {@code below}. */
    private static SkyLayer layer(String at, Map<String, Object> map, Set<Integer> below) {
        if (!map.containsKey("id")) throw new IllegalArgumentException(at + ": set \"id\" to a whole number from 1");
        int id = number(map, "id", 1, 99999, at);
        if (below.contains(id)) throw new IllegalArgumentException(at + ".id: another layer has id " + id);
        if (!map.containsKey("kind")) throw new IllegalArgumentException(at + ": set \"kind\" to one of " + keys(SkyLayer.Kind.values()));
        SkyLayer.Kind kind;
        try {
            kind = SkyLayer.Kind.byKey(string(map, "kind", at));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(at + ".kind: use one of " + keys(SkyLayer.Kind.values()), e);
        }
        SkyLayer.Blend blend = SkyLayer.Blend.NORMAL;
        try {
            if (map.containsKey("blend")) blend = SkyLayer.Blend.byKey(string(map, "blend", at));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(at + ".blend: use one of " + keys(SkyLayer.Blend.values()), e);
        }
        SkyLayer.Output output = SkyLayer.Output.SHOW;
        try {
            if (map.containsKey("output")) output = SkyLayer.Output.byKey(string(map, "output", at));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(at + ".output: use one of " + keys(SkyLayer.Output.values()), e);
        }
        int opacity = map.containsKey("opacity") ? number(map, "opacity", 0, 100, at) : 100;
        Map<SkyLayer.Param, Integer> params = new EnumMap<SkyLayer.Param, Integer>(SkyLayer.Param.class);
        for (SkyLayer.Param p : SkyLayer.Param.values()) {
            if (!map.containsKey(p.key)) continue;
            if (!kind.uses(p)) {
                throw new IllegalArgumentException(at + ": '" + p.key + "' has no meaning for " + kind.key() + ". It uses " + paramKeys(kind));
            }
            params.put(p, p.isColor() ? color(map, p.key, at) : number(map, p.key, p.min(), p.max(), at));
        }
        SkyMask mask = map.containsKey("mask") ? mask(at + ".mask", object(map.get("mask"), at + ".mask", MASK_KEYS), below) : SkyMask.NONE;
        Transform transform = map.containsKey("transform")
                ? transform(at + ".transform", object(map.get("transform"), at + ".transform", TRANSFORM_KEYS), kind) : Transform.IDENTITY;
        return new SkyLayer(id, kind, blend, opacity, output, mask, params, transform);
    }

    private static SkyMask mask(String at, Map<String, Object> map, Set<Integer> below) {
        if (!map.containsKey("layer")) throw new IllegalArgumentException(at + ": set \"layer\" to the id of a lower layer");
        int layer = number(map, "layer", 1, 99999, at);
        if (!below.contains(layer)) throw new IllegalArgumentException(at + ": no layer below this one has id " + layer);
        SkyMask.Mode mode = SkyMask.Mode.LIGHT;
        try {
            if (map.containsKey("mode")) mode = SkyMask.Mode.byKey(string(map, "mode", at));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(at + ".mode: use one of " + keys(SkyMask.Mode.values()), e);
        }
        return SkyMask.of(layer, mode);
    }

    private static Transform transform(String at, Map<String, Object> map, SkyLayer.Kind kind) {
        Transform t = Transform.IDENTITY;
        for (Transform.Axis a : Transform.Axis.values()) {
            String key = TRANSFORM_KEYS.get(a.ordinal());
            if (!map.containsKey(key)) continue;
            if (!kind.axes().contains(a)) {
                throw new IllegalArgumentException(at + ": '" + key + "' has no meaning for " + kind.key() + ". It uses " + axisKeys(kind));
            }
            t = t.with(a, number(map, key, Transform.min(a), Transform.max(a), at));
        }
        return t;
    }

    /** Writes the keys of a custom sky into the sky category. */
    static void encode(Map<String, Object> out, SkyDesign design) {
        if (design.celestial() != SkyDesign.Celestial.SHOW) out.put("celestial", design.celestial().key());
        if (design.light() != SkyDesign.Light.FIXED) out.put("light", design.light().key());
        List<Object> layers = new ArrayList<Object>();
        for (SkyLayer l : design.layers()) layers.add(encode(l));
        out.put("layers", layers);
    }

    private static Map<String, Object> encode(SkyLayer l) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", l.id());
        m.put("kind", l.kind().key());
        if (l.blend() != SkyLayer.Blend.NORMAL) m.put("blend", l.blend().key());
        if (l.opacity() != 100) m.put("opacity", l.opacity());
        if (l.output() != SkyLayer.Output.SHOW) m.put("output", l.output().key());
        if (!l.mask().isNone()) {
            Map<String, Object> mask = new LinkedHashMap<String, Object>();
            mask.put("layer", l.mask().layer());
            mask.put("mode", l.mask().mode().key());
            m.put("mask", mask);
        }
        for (SkyLayer.Param p : l.kind().params()) {
            m.put(p.key, p.isColor() ? (Object) Colors.format(l.param(p)) : l.param(p));
        }
        Map<String, Object> t = new LinkedHashMap<String, Object>();
        for (Transform.Axis a : l.kind().axes()) {
            if (l.transform().get(a) != Transform.identity(a)) t.put(TRANSFORM_KEYS.get(a.ordinal()), l.transform().get(a));
        }
        if (!t.isEmpty()) m.put("transform", t);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object o, String where, List<String> known) {
        if (!(o instanceof Map)) throw new IllegalArgumentException(where + " must be a JSON object");
        Map<String, Object> map = (Map<String, Object>) o;
        for (String key : map.keySet()) {
            if (!known.contains(key)) throw new IllegalArgumentException(where + ": unknown setting '" + key + "'. Use one of " + known);
        }
        return map;
    }

    private static int number(Map<String, Object> m, String key, int min, int max, String where) {
        Object o = m.get(key);
        double v = o instanceof Number ? ((Number) o).doubleValue() : Double.NaN;
        if (v != Math.rint(v) || v < min || v > max) {
            throw new IllegalArgumentException(where + "." + key + ": use a whole number from " + min + " to " + max);
        }
        return (int) v;
    }

    private static int color(Map<String, Object> m, String key, String where) {
        try {
            return Colors.parse(string(m, key, where));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + "." + key + ": use a color such as \"#FF8000\"", e);
        }
    }

    private static String string(Map<String, Object> m, String key, String where) {
        Object o = m.get(key);
        if (!(o instanceof String)) throw new IllegalArgumentException(where + "." + key + " must be a string");
        return (String) o;
    }

    private static List<String> paramKeys(SkyLayer.Kind kind) {
        List<String> out = new ArrayList<String>();
        for (SkyLayer.Param p : kind.params()) out.add(p.key);
        if (!kind.axes().isEmpty()) out.add("transform");
        return out;
    }

    private static List<String> axisKeys(SkyLayer.Kind kind) {
        List<String> out = new ArrayList<String>();
        for (Transform.Axis a : kind.axes()) out.add(TRANSFORM_KEYS.get(a.ordinal()));
        return out;
    }

    private static List<String> keys(Enum<?>[] values) {
        List<String> out = new ArrayList<String>();
        for (Enum<?> v : values) out.add(v.name().toLowerCase(Locale.ROOT));
        return out;
    }
}
