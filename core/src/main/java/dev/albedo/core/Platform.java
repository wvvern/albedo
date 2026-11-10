package dev.albedo.core;

import dev.albedo.core.dev.Harness;

import java.io.File;

/** The port each loader and version adapter implements, which is the only way the core reaches the game. */
public interface Platform {
    /** A name for logs, for example "Fabric 1.21.1". */
    String describe();

    /** The game's config folder, which gets a folder of the core inside. */
    File configDir();

    void info(String message);

    void warn(String message, Throwable error);

    /** Reloads all resources and rebuilds chunks on the client thread, for what a repaint cannot reach. */
    void reloadResources();

    /**
     * Sends the uploads of a repaint to the GPU on the client thread, also after a failed upload.
     *
     * @return false when the uploads could not be sent, which makes the core reload resources
     */
    default boolean finishRepaint() {
        return true;
    }

    /** Rebuilds the chunk meshes, which hold block tints and lighting, on the client thread. */
    void rebuildChunks();

    /** Shows a short message to the player in the action bar, on the client thread. */
    void showMessage(String message);

    boolean isKeyDown(Keys.Key key);

    /** The address of the multiplayer server the player is on, or null in single player and the menus. */
    String currentServer();

    /** Shows the settings screen, a version screen that wraps {@link dev.albedo.core.ui.SettingsEditor}. */
    void openSettings();

    String clipboard();

    void setClipboard(String text);

    /** The development test harness host; only used when {@code -Dalbedo.harness=<dir>} is set. */
    Harness.Host harnessHost();
}
