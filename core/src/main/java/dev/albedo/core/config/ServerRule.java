package dev.albedo.core.config;

import java.util.Locale;
import java.util.Objects;

/** The profile a multiplayer server uses, which applies also to its subdomains, so "hypixel.net" matches "mc.hypixel.net". */
public final class ServerRule {
    private final String server;
    private final String profile;

    /**
     * @param server a host name, which is normalized
     * @param profile the profile name, or null to turn the mod off on the server
     */
    public ServerRule(String server, String profile) {
        this.server = host(server);
        this.profile = profile;
    }

    public String server() { return server; }

    /** The profile name, or null when the mod is off on the server. */
    public String profile() { return profile; }

    public ServerRule withProfile(String name) {
        return new ServerRule(server, name);
    }

    /** Whether the rule applies to a normalized host, where an IP address must match exactly. */
    boolean matches(String host, boolean ipAddress) {
        return host.equals(server) || !ipAddress && host.endsWith("." + server);
    }

    static boolean isIpAddress(String host) {
        if (host.indexOf(':') >= 0) return true;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c != '.' && (c < '0' || c > '9')) return false;
        }
        return true;
    }

    /** The host of a server address in lower case, without the port and the final dot, or "" for none. */
    public static String host(String address) {
        if (address == null) return "";
        String h = address.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[")) {
            int end = h.indexOf(']');
            return end < 0 ? h.substring(1) : h.substring(1, end);
        }
        int colon = h.lastIndexOf(':');
        if (colon >= 0 && h.indexOf(':') == colon) h = h.substring(0, colon); // an IPv6 address has more than one colon
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ServerRule)) return false;
        ServerRule r = (ServerRule) o;
        return r.server.equals(server) && Objects.equals(r.profile, profile);
    }

    @Override
    public int hashCode() { return Objects.hash(server, profile); }
}
