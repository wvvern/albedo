package dev.albedo.legacy;

import dev.albedo.core.Keys;
import dev.albedo.core.Platform;
import dev.albedo.core.dev.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.ForgeVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.File;

final class LegacyPlatform implements Platform {
    private static final Logger LOG = LogManager.getLogger("albedo");
    private static final int CHUNK_BLOCKS = 16;
    /** The highest block y of 1.8.9 and 1.12.2. */
    private static final int TOP_Y = 255;

    @Override
    public String describe() {
        return "Forge " + ForgeVersion.mcVersion;
    }

    @Override
    public File configDir() {
        return Minecraft.getMinecraft() != null && Game.gameDir() != null ? new File(Game.gameDir(), "config") : new File("config");
    }

    @Override public void info(String message) { LOG.info(message); }

    @Override public void warn(String message, Throwable error) { LOG.warn(message, error); }

    @Override
    public void reloadResources() {
        final Minecraft mc = Minecraft.getMinecraft();
        long start = System.nanoTime();
        LegacyChroma.restore();
        // refreshResources also rebuilds the chunks.
        ReloadScreen.run(() -> mc.refreshResources());
        info("Reloaded all resources in " + (System.nanoTime() - start) / 1000000 + " ms");
    }

    /**
     * Marks all chunks in render distance for a new mesh without loadRenderers, which with some mods (Patcher,
     * Essential and OptiFine L5 together) leaves the chunks empty until the player moves.
     */
    @Override
    public void rebuildChunks() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (mc.renderGlobal == null || view == null) return;
        int r = (mc.gameSettings.renderDistanceChunks + 1) * CHUNK_BLOCKS;
        int x = Game.floor(view.posX), z = Game.floor(view.posZ);
        mc.renderGlobal.markBlockRangeForRenderUpdate(x - r, 0, z - r, x + r, TOP_Y, z + r);
    }

    @Override
    public void showMessage(String message) {
        Game.showMessage(message);
    }

    @Override
    public boolean isKeyDown(Keys.Key key) {
        return Keyboard.isCreated() && Keyboard.isKeyDown(key.lwjgl2);
    }

    @Override
    public String currentServer() {
        Minecraft mc = Minecraft.getMinecraft();
        return Game.world() == null || mc.isSingleplayer() ? null : LegacyHooks.connectedHost();
    }

    @Override
    public void openSettings() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(new GuiSettings(mc.currentScreen));
    }

    @Override
    public String clipboard() {
        return GuiScreen.getClipboardString();
    }

    @Override
    public void setClipboard(String text) {
        GuiScreen.setClipboardString(text);
    }

    @Override
    public Harness.Host harnessHost() {
        return new LegacyHarnessHost();
    }
}
