package dev.albedo.legacy;

import dev.albedo.core.Keys;
import dev.albedo.core.Platform;
import dev.albedo.core.dev.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.File;

final class LegacyPlatform implements Platform {
    private static final Logger LOG = LogManager.getLogger("albedo");

    @Override
    public String describe() {
        return "Forge 1.8.9";
    }

    @Override
    public File configDir() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.mcDataDir != null ? new File(mc.mcDataDir, "config") : new File("config");
    }

    @Override public void info(String message) { LOG.info(message); }

    @Override public void warn(String message, Throwable error) { LOG.warn(message, error); }

    @Override
    public void reloadResources() {
        final Minecraft mc = Minecraft.getMinecraft();
        long start = System.nanoTime();
        LegacyChroma.restore();
        // refreshResources also rebuilds the chunks.
        ReloadScreen.run(new Runnable() {
            @Override
            public void run() {
                mc.refreshResources();
            }
        });
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
        int r = (mc.gameSettings.renderDistanceChunks + 1) * 16;
        int x = MathHelper.floor_double(view.posX), z = MathHelper.floor_double(view.posZ);
        mc.renderGlobal.markBlockRangeForRenderUpdate(x - r, 0, z - r, x + r, 255, z + r);
    }

    @Override
    public void showMessage(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI != null) mc.ingameGUI.setRecordPlaying(message, false); // the 1.8 action bar
    }

    @Override
    public boolean isKeyDown(Keys.Key key) {
        return Keyboard.isCreated() && Keyboard.isKeyDown(key.lwjgl2);
    }

    @Override
    public String currentServer() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld == null || mc.isSingleplayer() ? null : LegacyHooks.connectedHost();
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
