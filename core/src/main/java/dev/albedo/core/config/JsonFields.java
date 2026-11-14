package dev.albedo.core.config;

import dev.albedo.core.Colors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The value readers of both codecs, where each error starts with the {@code place} of the caller and only an absent
 * key is missing, so a JSON null value is an error.
 */
final class JsonFields {
    private JsonFields() {}

    /** A JSON object; with {@code known}, any other key is an error naming the allowed ones. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object o, String where, List<String> known) {
        if (!(o instanceof Map)) throw new IllegalArgumentException(where + ": use a JSON object");
        Map<String, Object> map = (Map<String, Object>) o;
        if (known != null) {
            for (String key : map.keySet()) {
                if (!known.contains(key)) throw new IllegalArgumentException(where + ": unknown setting '" + key + "'. Use one of " + known);
            }
        }
        return map;
    }

    /** The keys of enum values in the file: their names in lower case. */
    static List<String> keys(Collection<? extends Enum<?>> values) {
        List<String> out = new ArrayList<String>();
        for (Enum<?> v : values) out.add(v.name().toLowerCase(Locale.ROOT));
        return out;
    }

    /** The enum value that {@code key} names in lower case, or {@code def} when the key is not there. */
    static <E extends Enum<E>> E choice(Map<String, Object> m, String key, E def, String place) {
        if (!m.containsKey(key)) return def;
        Class<E> type = def.getDeclaringClass();
        try {
            return Enum.valueOf(type, string(m, key, place).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(place + ": use one of " + keys(Arrays.asList(type.getEnumConstants())), e);
        }
    }

    static boolean bool(Map<String, Object> m, String key, String place) {
        Object o = m.get(key);
        if (!(o instanceof Boolean)) throw new IllegalArgumentException(place + ": use true or false");
        return (Boolean) o;
    }

    /** The whole number at {@code key}, or {@code def} when the key is not there. */
    static int number(Map<String, Object> m, String key, int min, int max, int def, String place) {
        return m.containsKey(key) ? number(m, key, min, max, place) : def;
    }

    static int number(Map<String, Object> m, String key, int min, int max, String place) {
        Object o = m.get(key);
        double v = o instanceof Number ? ((Number) o).doubleValue() : Double.NaN;
        if (v != Math.rint(v) || v < min || v > max) {
            throw new IllegalArgumentException(place + ": use a whole number from " + min + " to " + max);
        }
        return (int) v;
    }

    static String string(Map<String, Object> m, String key, String place) {
        Object o = m.get(key);
        if (!(o instanceof String)) throw new IllegalArgumentException(place + ": use a string");
        return (String) o;
    }

    static int color(Map<String, Object> m, String key, String place) {
        try {
            return Colors.parse(string(m, key, place));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(place + ": use a color such as \"#FF8000\"", e);
        }
    }
}
