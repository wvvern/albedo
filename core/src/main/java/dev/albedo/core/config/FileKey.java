package dev.albedo.core.config;

import java.util.Locale;

/** An enum value whose key in the config file is its name in lower case. */
public interface FileKey {
    String name();

    default String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
