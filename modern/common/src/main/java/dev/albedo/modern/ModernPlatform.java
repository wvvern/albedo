package dev.albedo.modern;

import com.mojang.blaze3d.platform.InputConstants;
import dev.albedo.core.Keys;
import dev.albedo.core.Platform;
import dev.albedo.core.dev.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** The {@link Platform} for Minecraft 1.21 and later on all loaders, which keeps loader code in the entrypoints. */
final class ModernPlatform implements Platform {
    static final String MINECRAFT = /*$ minecraft*/ "26.3";
    private static final Logger LOG = LoggerFactory.getLogger("albedo");

    private final Map<String, Integer> keyCodes = new HashMap<>();
    private final File loaderConfigDir;

    /** Takes the config folder of the loader, or null when a mixin starts the mod before the loader entrypoint. */
    ModernPlatform(File loaderConfigDir) {
        this.loaderConfigDir = loaderConfigDir;
    }

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
        if (loaderConfigDir != null) return loaderConfigDir;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.gameDirectory != null ? new File(mc.gameDirectory, "config") : new File("config");
    }

    @Override public void info(String message) { LOG.info(message); }

    @Override public void warn(String message, Throwable error) { LOG.warn(message, error); }

    @Override
    public void reloadResources() {
        long start = System.nanoTime();
        Minecraft.getInstance().reloadResourcePacks()
                .thenRun(() -> info("Reloaded all resources in " + (System.nanoTime() - start) / 1000000 + " ms"));
    }

    @Override
    public boolean finishRepaint() {
        try {
            TextureUploads.flush();
            return true;
        } catch (RuntimeException e) {
            warn("Cannot draw the repainted sprites", e);
            return false;
        }
    }

    @Override
    public void rebuildChunks() {
        // The same step as after a resource reload, which Sodium replaces with its own rebuild.
        //? if >=26.1 {
        Minecraft.getInstance().levelExtractor.allChanged();
        //?} else {
        /*Minecraft.getInstance().levelRenderer.allChanged();
        *///?}
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
    public String currentServer() {
        Minecraft mc = Minecraft.getInstance();
        ServerData data = mc.getCurrentServer();
        return mc.level == null || mc.isLocalServer() || data == null ? null : data.ip;
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
    public void setClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
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
