package dev.albedo.core;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Case-insensitive glob over texture ids such as {@code minecraft:oak_leaves}.
 * {@code *} matches any run of characters, {@code ?} one character. A pattern without a ':' matches any namespace.
 */
public final class Glob {
    private final Pattern pattern;

    private Glob(Pattern pattern) {
        this.pattern = pattern;
    }

    public static Glob compile(String glob) {
        String g = glob.trim().toLowerCase(Locale.ROOT);
        if (g.indexOf(':') < 0) g = "*:" + g;
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < g.length(); i++) {
            char c = g.charAt(i);
            if (c == '*') re.append(".*");
            else if (c == '?') re.append('.');
            else re.append(Pattern.quote(String.valueOf(c)));
        }
        return new Glob(Pattern.compile(re.toString()));
    }

    public boolean matches(String id) {
        return pattern.matcher(id.toLowerCase(Locale.ROOT)).matches();
    }
}
