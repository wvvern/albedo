package dev.albedo.core;

import dev.albedo.core.dev.Harness;
import dev.albedo.core.dev.HookCounter;

import java.io.File;
import java.util.List;

/** The port from the development harness to the game; implemented next to each version's adapter. */
public interface HarnessHost {
    boolean inWorld();

    /** Whether the game still shows its loading screen, where no command runs, because the models can be missing. */
    default boolean loading() { return false; }

    void openWorld(String name);

    void runCommand(String command);

    /** Writes the next rendered frame to {@code png}, then calls {@link dev.albedo.core.dev.HarnessLog#log}. */
    void screenshot(File png);

    void setHudVisible(boolean visible);

    void closeScreen();

    /**
     * Moves the pointer to a GUI position and presses or releases the left button through the game's own mouse handler,
     * as a real mouse does, and returns false when this version cannot do that.
     */
    boolean mouseButton(int guiX, int guiY, boolean down);

    /**
     * Sends a press or a release of a key into the entry point of the game that the window events call, as a real
     * keyboard does, and returns false when this version cannot do that.
     *
     * @param ctrl whether a Control key is held, for the versions that read it from the event
     */
    boolean key(Keys.Key key, boolean down, boolean ctrl);

    /**
     * Sends one typed character into the entry point of the game that the window events call, and returns false when
     * the window would drop it, for example because the game did not start text input.
     *
     * @param afterKey whether the key of the character went down just before, else the window sends the character as
     *        typed text with no key, as for a character that needs Shift or AltGr
     */
    boolean character(char c, boolean afterKey);

    boolean settingsOpen();

    void quit();

    /**
     * Mutes sound, disables the narrator, keeps the game running without focus and keeps the camera where the harness
     * aims it, and must be cheap because it runs every tick.
     */
    void quiet();

    /** Sets the field of view in degrees, in the range of the game option. */
    void setFov(int degrees);

    /**
     * Makes the draws of {@code draw} with the sharp sky shader, and later gives the pixels and the GPU time of one
     * draw to {@code done}.
     *
     * @return false while the shader compiles, or when this version or GPU has no sharp sky
     */
    boolean drawSharpSky(Harness.SkyDraw draw, Harness.SkyDone done);

    /**
     * Measures the GPU and CPU time of the GPU pass of Chroma in each of the next frames, then logs the times, the
     * atlas sizes and the GPU memory of the pass.
     *
     * @return false when this version has no GPU pass of Chroma, or the pass does not run now
     */
    boolean timeChroma(int frames);

    /** Gives the pixels of the last frame that the game drew to {@code done}, on any thread. */
    void framePixels(Harness.FramePixels done);

    /** The counters of the hooks of this version that can miss their target with no error. */
    List<HookCounter> hookCounters();

    /** Whether the next frame draws clouds with visible pixels, from the state that the renderer reads. */
    boolean cloudsDrawn();
}
