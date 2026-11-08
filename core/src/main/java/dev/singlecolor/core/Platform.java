package dev.singlecolor.core;

import java.io.File;
import java.io.IOException;

/**
 * The port each loader/version adapter implements. This is the only way the core reaches the game, so the core
 * never has to change when Minecraft does.
 */
public interface Platform {
    /** e.g. "Fabric 1.21.1"; for logs and the about message. */
    String describe();

    /** The game's config directory (the core puts its own folder inside). */
    File configDir();

    void info(String message);

    void warn(String message, Throwable error);

    /** Reload resource packs/textures and rebuild chunks. Called on the client thread. */
    void reloadResources();

    /** Show a short message to the player (chat or action bar). Called on the client thread. */
    void showMessage(String message);

    /** Whether the key is held right now. Adapters pick the code family they use (GLFW or LWJGL 2). */
    boolean isKeyDown(Keys.Key key);

    /** Decode a PNG from disk into ARGB pixels, using whatever image class this version ships. */
    PixelBuffer readImage(File png) throws IOException;
}
