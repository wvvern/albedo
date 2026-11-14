package dev.albedo.core;

import java.io.File;

/** A platform with no game: it counts reloads, rebuilds and uploads, and keeps the server and the clipboard in fields. */
public final class FakePlatform implements Platform {
    private static final String CLIPBOARD = "#123456";

    private final File dir;
    public int reloads, rebuilds, uploads;
    public boolean flushFails;
    public String server;
    public String clipboard = CLIPBOARD;

    public FakePlatform(File dir) {
        this.dir = dir;
    }

    public void resetCounters() {
        reloads = rebuilds = uploads = 0;
    }

    /** Resets the counters, the flush and the clipboard, but not the server, which changes only through a client tick. */
    public void reset() {
        resetCounters();
        flushFails = false;
        clipboard = CLIPBOARD;
    }

    @Override public String describe() { return "test"; }
    @Override public File configDir() { return dir; }
    @Override public void info(String message) {}
    @Override public void warn(String message, Throwable error) {}
    @Override public String currentServer() { return server; }
    @Override public void reloadResources() { reloads++; }
    @Override public boolean finishRepaint() { return !flushFails; }
    @Override public void rebuildChunks() { rebuilds++; }
    @Override public void showMessage(String message) {}
    @Override public boolean isKeyDown(Keys.Key key) { return false; }
    @Override public void openSettings() {}
    @Override public String clipboard() { return clipboard; }
    @Override public void setClipboard(String text) { clipboard = text; }
    @Override public HarnessHost harnessHost() { return null; }
}
