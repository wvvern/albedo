package dev.albedo.core.texture;

/** The path of a texture location without the parts that every resource file has. */
final class ResourcePath {
    private ResourcePath() {}

    /** The location with no namespace, {@code textures/} prefix or {@code .png} suffix; the case stays. */
    static String strip(String location) {
        String s = location.substring(location.indexOf(':') + 1);
        if (s.startsWith("textures/")) s = s.substring("textures/".length());
        if (s.endsWith(".png")) s = s.substring(0, s.length() - 4);
        return s;
    }
}
