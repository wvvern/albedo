package dev.albedo.core;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A case-insensitive pattern over texture ids such as {@code minecraft:oak_leaves}. Two forms:
 * <ul>
 *   <li>Glob: {@code *} matches any run of characters, {@code ?} one character; it must match the whole id.</li>
 *   <li>Regular expression between slashes, for example {@code /^(oak|birch)_/}; it may match any part of the id.</li>
 * </ul>
 * A pattern without a ':' ignores the namespace, so {@code stone} matches {@code minecraft:stone} and
 * {@code /^oak/} matches {@code minecraft:oak_log}.
 */
public final class Glob {
    private final Pattern pattern;
    private final boolean regex;
    private final boolean withNamespace;

    private Glob(Pattern pattern, boolean regex, boolean withNamespace) {
        this.pattern = pattern;
        this.regex = regex;
        this.withNamespace = withNamespace;
    }

    /** @throws IllegalArgumentException for a regular expression that is not valid; see {@link #problem} */
    public static Glob compile(String text) {
        String g = text.trim();
        if (isRegex(g)) {
            String body = g.substring(1, g.length() - 1);
            return new Glob(Pattern.compile(body, Pattern.CASE_INSENSITIVE), true, body.indexOf(':') >= 0);
        }
        g = g.toLowerCase(Locale.ROOT);
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < g.length(); i++) {
            char c = g.charAt(i);
            if (c == '*') re.append(".*");
            else if (c == '?') re.append('.');
            else re.append(Pattern.quote(String.valueOf(c)));
        }
        return new Glob(Pattern.compile(re.toString()), false, g.indexOf(':') >= 0);
    }

    /** True for text between two slashes, such as {@code /leaves$/}. */
    public static boolean isRegex(String text) {
        String t = text.trim();
        return t.length() >= 2 && t.charAt(0) == '/' && t.charAt(t.length() - 1) == '/';
    }

    /** Why {@code text} is not a valid pattern, or null when it is. */
    public static String problem(String text) {
        if (!isRegex(text)) return null;
        try {
            compile(text);
            return null;
        } catch (PatternSyntaxException e) {
            return e.getDescription() + (e.getIndex() >= 0 ? " at character " + (e.getIndex() + 1) : "");
        }
    }

    public boolean matches(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        if (!withNamespace) s = s.substring(s.indexOf(':') + 1);
        return regex ? pattern.matcher(s).find() : pattern.matcher(s).matches();
    }
}
