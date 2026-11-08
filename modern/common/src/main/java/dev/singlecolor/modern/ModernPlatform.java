package dev.singlecolor.modern;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.singlecolor.core.Keys;
import dev.singlecolor.core.PixelBuffer;
import dev.singlecolor.core.Platform;
import dev.singlecolor.core.dev.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** {@link Platform} for Minecraft 1.21+ on any loader. Loader-specific code lives only in the tiny entrypoints. */
final class ModernPlatform implements Platform {
    static final String MINECRAFT = /*$ minecraft*/ "26.3";
    private static final Logger LOG = LoggerFactory.getLogger("singlecolor");

    private final Map<String, Integer> keyCodes = new HashMap<>();

    @Override
    public String describe() {
        String loader = classExists("net.neoforged.fml.loading.FMLLoader") ? "NeoForge" : "Fabric";
        return loader + " " + MINECRAFT;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, ModernPlatform.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public File configDir() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.gameDirectory != null ? new File(mc.gameDirectory, "config") : new File("config");
    }

    @Override public void info(String message) { LOG.info(message); }

    @Override public void warn(String message, Throwable error) { LOG.warn(message, error); }

    @Override
    public void reloadResources() {
        Minecraft.getInstance().reloadResourcePacks();
    }

    @Override
    public void showMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.1 {
        if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(message));
        //?} else {
        /*mc.gui.setOverlayMessage(Component.literal(message), false);
        *///?}
    }

    @Override
    public boolean isKeyDown(Keys.Key key) {
        Integer code = keyCodes.get(key.minecraftName);
        if (code == null) {
            try {
                // Resolved by name: GLFW codes on 1.21.x, SDL scancodes on 26.x.
                code = InputConstants.getKey(key.minecraftName).getValue();
            } catch (RuntimeException e) {
                code = -1;
            }
            keyCodes.put(key.minecraftName, code);
        }
        if (code < 0) return false;
        //? if >=26.1 {
        return InputConstants.isKeyDown(code);
        //?} elif >=1.21.9 {
        /*return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), code);
        *///?} else {
        /*return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), code);
        *///?}
    }

    @Override
    public PixelBuffer readImage(File png) throws IOException {
        try (InputStream in = new FileInputStream(png); NativeImage image = NativeImage.read(in)) {
            if (!NativeImagePixels.supports(image)) throw new IOException(png + " must be an RGBA PNG");
            return PixelBuffer.Heap.copyOf(new NativeImagePixels(image));
        }
    }

    @Override
    public void openSettings() {
        setScreen(new SettingsScreen(currentScreen()));
    }

    @Override
    public String clipboard() {
        return Minecraft.getInstance().keyboardHandler.getClipboard();
    }

    @Override
    public Harness.Host harnessHost() {
        return new ModernHarnessHost();
    }

    static Screen currentScreen() {
        //? if >=26.1 {
        return Minecraft.getInstance().gui.screen();
        //?} else {
        /*return Minecraft.getInstance().screen;
        *///?}
    }

    static void setScreen(Screen screen) {
        //? if >=26.1 {
        Minecraft.getInstance().gui.setScreen(screen);
        //?} else {
        /*Minecraft.getInstance().setScreen(screen);
        *///?}
    }
}
