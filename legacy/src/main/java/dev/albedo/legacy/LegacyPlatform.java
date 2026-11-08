package dev.albedo.legacy;

import dev.albedo.core.Keys;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Platform;
import dev.albedo.core.dev.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** {@link Platform} for Minecraft 1.8.9 (Forge). */
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
        Minecraft mc = Minecraft.getMinecraft();
        mc.refreshResources();
        rebuildChunks();
    }

    @Override
    public void rebuildChunks() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
    }

    @Override
    public boolean shadingInModels() {
        return true; // FaceBakery bakes the darker block sides into the model quads
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
    public PixelBuffer readImage(File png) throws IOException {
        BufferedImage image = ImageIO.read(png);
        if (image == null) throw new IOException("Not an image: " + png);
        return PixelBuffer.Heap.copyOf(new BufferedImagePixels(BufferedImagePixels.toArgb(image)));
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
