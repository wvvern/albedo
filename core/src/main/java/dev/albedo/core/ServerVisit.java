package dev.albedo.core;

import dev.albedo.core.config.Config;
import dev.albedo.core.config.ServerRule;

import java.util.Objects;

/** The visit of the player on a multiplayer server: the host and the state the player chose there. */
final class ServerVisit {
    /** The host of the server the player is on, or null. */
    private String host;
    /** The state the player chose on this server with F6, F7 or the settings, which lasts until the player leaves. */
    private Config.State chosen;

    /** The host of the server the player is on, or null in single player and the menus. */
    String host() {
        return host;
    }

    /**
     * Starts a new visit and forgets the chosen state.
     *
     * @param next the host the player is on now, or null when the player leaves
     * @return false when the player is still on the same host, which changes nothing
     */
    boolean join(String next) {
        if (Objects.equals(next, host)) return false;
        host = next;
        chosen = null;
        return true;
    }

    /**
     * Records a state the player chose.
     *
     * @param next the config to save, whose rule decides where the state goes
     * @return the config to save: {@code next} on a server with a rule, else {@code next} with the state
     */
    Config choose(Config next, Config.State state) {
        if (host != null && next.serverRule(host) != null) {
            chosen = state;
            return next;
        }
        return next.withState(state);
    }

    /** The config in effect: the stored config with the chosen state or the state of the rule of the server. */
    Config effective(Config stored) {
        ServerRule rule = host == null ? null : stored.serverRule(host);
        // The chosen state belongs to the rule, so a removed rule takes it away too.
        if (rule == null) chosen = null;
        if (chosen != null && stored.profiles().containsKey(chosen.profile)) return stored.withState(chosen);
        if (rule != null && rule.profile() == null) return stored.withEnabled(false);
        if (rule != null) return stored.withState(new Config.State(true, rule.profile()));
        return stored;
    }
}
