package dev.plainview.legacy;

import dev.plainview.core.dev.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import java.io.File;
import java.util.List;

/** Development-only: what the test harness needs from 1.8.9 (see {@link Harness}). */
final class LegacyHarnessHost implements Harness.Host {
    @Override
    public boolean inWorld() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld != null && mc.thePlayer != null;
    }

    @Override
    public void openWorld(String name) {
        // Loads the save if it exists; the settings are only used to create it.
        WorldSettings settings = new WorldSettings(42L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT).enableCommands();
        Minecraft.getMinecraft().launchIntegratedServer(name, name, settings);
    }

    @Override
    public void runCommand(final String command) {
        final MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
        if (server == null) {
            Harness.log("! no singleplayer server");
            return;
        }
        server.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
                if (players.isEmpty()) Harness.log("! no player for: " + command);
                else server.getCommandManager().executeCommand(players.get(0), command);
            }
        });
    }

    @Override
    public void screenshot(File png) {
        Minecraft mc = Minecraft.getMinecraft();
        // saveScreenshot writes <dir>/screenshots/<name>; move it next to the other harness output.
        File dir = png.getParentFile();
        ScreenShotHelper.saveScreenshot(dir, png.getName(), mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        File written = new File(new File(dir, "screenshots"), png.getName());
        png.delete();
        Harness.log(written.renameTo(png) ? "saved " + png.getName() : "! screenshot failed: " + png.getName());
    }

    @Override
    public void setHudVisible(boolean visible) {
        Minecraft.getMinecraft().gameSettings.hideGUI = !visible;
    }

    @Override
    public void closeScreen() {
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

    @Override
    public void quit() {
        Minecraft.getMinecraft().shutdown();
    }

    @Override
    public void quiet() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.getSoundLevel(SoundCategory.MASTER) != 0f) mc.gameSettings.setSoundLevel(SoundCategory.MASTER, 0f);
        mc.gameSettings.pauseOnLostFocus = false;
    }
}
