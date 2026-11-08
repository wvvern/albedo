package dev.worldpainter.core.config;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny JSON reader/writer so the core needs no library (Gson's API differs across the Minecraft versions we span).
 * Values map to: Map (object, insertion ordered), List, String, Double, Boolean, null.
 * The reader also accepts // line comments and trailing commas, since people edit this file by hand.
 */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skip();
        Object v = p.value();
        p.skip();
        if (p.i != p.s.length()) throw p.error("unexpected trailing content");
        return v;
    }

    // ---------------------------------------------------------------- reading

    private Object value() {
        if (i >= s.length()) throw error("unexpected end of input");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return number();
                throw error("unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        i++;
        skip();
        while (peek() != '}') {
            if (peek() != '"') throw error("expected a quoted key");
            String key = string();
            skip();
            expect(':');
            skip();
            map.put(key, value());
            skip();
            if (peek() == ',') { i++; skip(); } else if (peek() != '}') throw error("expected ',' or '}'");
        }
        i++;
        return map;
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<Object>();
        i++;
        skip();
        while (peek() != ']') {
            list.add(value());
            skip();
            if (peek() == ',') { i++; skip(); } else if (peek() != ']') throw error("expected ',' or ']'");
        }
        i++;
        return list;
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        i++;
        while (true) {
            if (i >= s.length()) throw error("unterminated string");
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            if (i >= s.length()) throw error("unterminated escape");
            char e = s.charAt(i++);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u':
                    if (i + 4 > s.length()) throw error("bad unicode escape");
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: sb.append(e);
            }
        }
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        try {
            return Double.valueOf(s.substring(start, i));
        } catch (NumberFormatException e) {
            throw error("bad number");
        }
    }

    private void literal(String word) {
        if (!s.startsWith(word, i)) throw error("expected " + word);
        i += word.length();
    }

    private char peek() {
        if (i >= s.length()) throw error("unexpected end of input");
        return s.charAt(i);
    }

    private void expect(char c) {
        if (peek() != c) throw error("expected '" + c + "'");
        i++;
    }

    private void skip() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
            else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
            } else break;
        }
    }

    private IllegalArgumentException error(String msg) {
        int line = 1, col = 1;
        for (int k = 0; k < i && k < s.length(); k++) {
            if (s.charAt(k) == '\n') { line++; col = 1; } else col++;
        }
        return new IllegalArgumentException("JSON error at line " + line + ", column " + col + ": " + msg);
    }

    // ---------------------------------------------------------------- writing

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.append('\n').toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder sb, Object v, int indent) {
        if (v == null) sb.append("null");
        else if (v instanceof String) quote(sb, (String) v);
        else if (v instanceof Boolean) sb.append(v);
        else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) sb.append((long) d); else sb.append(d);
        } else if (v instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) v;
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append("{\n");
            Iterator<Map.Entry<String, Object>> it = m.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> e = it.next();
                pad(sb, indent + 1);
                quote(sb, e.getKey());
                sb.append(": ");
                write(sb, e.getValue(), indent + 1);
                if (it.hasNext()) sb.append(',');
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append('}');
        } else if (v instanceof List) {
            List<Object> l = (List<Object>) v;
            boolean simple = true;
            for (Object o : l) if (o instanceof Map || o instanceof List) simple = false;
            if (simple) {
                sb.append('[');
                for (int k = 0; k < l.size(); k++) {
                    if (k > 0) sb.append(", ");
                    write(sb, l.get(k), indent);
                }
                sb.append(']');
                return;
            }
            sb.append("[\n");
            for (int k = 0; k < l.size(); k++) {
                pad(sb, indent + 1);
                write(sb, l.get(k), indent + 1);
                if (k + 1 < l.size()) sb.append(',');
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append(']');
        } else throw new IllegalArgumentException("cannot write " + v.getClass());
    }

    private static void pad(StringBuilder sb, int indent) {
        for (int k = 0; k < indent; k++) sb.append("  ");
    }

    private static void quote(StringBuilder sb, String str) {
        sb.append('"');
        for (int k = 0; k < str.length(); k++) {
            char c = str.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                case '\r': sb.append("\\r"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        sb.append('"');
    }
}
