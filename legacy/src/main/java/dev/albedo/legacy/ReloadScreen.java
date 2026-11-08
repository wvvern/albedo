package dev.albedo.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

import java.io.File;

/**
 * A loading screen with a progress bar while a full resource reload blocks the client thread, so that the game does
 * not look frozen. The reload calls {@link #begin} and {@link #step} for each reload listener (ReloadListenersMixin)
 * and {@link #load} for each texture. The bar moves one part for each listener, and in a part it moves with the
 * textures. It draws at most each {@link #FRAME_MS} ms. LoadingScreenRenderer is not used: Patcher can turn it off.
 */
final class ReloadScreen {
    private static final long FRAME_MS = 100;
    private static final String TITLE = "Albedo: reloading resources";

    private static boolean active;
    private static int step;
    private static int steps;
    private static int loads;
    private static long lastFrame;
    private static boolean screenshot;

    private ReloadScreen() {}

    /** Runs {@code reload} on the client thread and shows the screen until it ends. */
    static void run(Runnable reload) {
        active = true;
        step = 0;
        steps = 0;
        loads = 0;
        screenshot = System.getProperty("albedo.harness") != null;
        draw(true);
        try {
            reload.run();
        } finally {
            active = false;
        }
    }

    static void begin(int listeners) {
        steps = listeners;
        step = 0;
    }

    static void step() {
        step++;
        loads = 0;
        draw(true);
    }

    static void load() {
        loads++;
        draw(false);
    }

    private static void draw(boolean force) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!active || !mc.isCallingFromMinecraftThread() || mc.fontRendererObj == null) return;
        long now = Minecraft.getSystemTime();
        if (!force && now - lastFrame < FRAME_MS) return;
        lastFrame = now;
        // Each listener has an equal part. In a part, the bar moves toward its end as the textures load.
        float part = steps <= 0 ? 0f : 1f / steps;
        float progress = steps <= 0 ? 0f : Math.max(0, step - 1) * part + part * loads / (loads + 200f);

        Framebuffer fb = mc.getFramebuffer();
        fb.bindFramebuffer(true);
        ScaledResolution sr = new ScaledResolution(mc);
        int w = sr.getScaledWidth(), h = sr.getScaledHeight();
        GlStateManager.clearColor(0f, 0f, 0f, 1f);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, w, h, 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);

        mc.getTextureManager().bindTexture(Gui.optionsBackground);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        wr.pos(0, h, 0).tex(0, h / 32.0).color(64, 64, 64, 255).endVertex();
        wr.pos(w, h, 0).tex(w / 32.0, h / 32.0).color(64, 64, 64, 255).endVertex();
        wr.pos(w, 0, 0).tex(w / 32.0, 0).color(64, 64, 64, 255).endVertex();
        wr.pos(0, 0, 0).tex(0, 0).color(64, 64, 64, 255).endVertex();
        tessellator.draw();

        FontRenderer font = mc.fontRendererObj;
        font.drawStringWithShadow(TITLE, (w - font.getStringWidth(TITLE)) / 2f, h / 2f - 20, 0xFFFFFF);
        String detail = steps > 0 ? "Step " + Math.max(1, step) + " of " + steps : "Starting";
        font.drawStringWithShadow(detail, (w - font.getStringWidth(detail)) / 2f, h / 2f - 6, 0xA0A0A0);
        int barX = w / 2 - 50, barY = h / 2 + 10;
        Gui.drawRect(barX, barY, barX + 100, barY + 2, 0xFF808080);
        Gui.drawRect(barX, barY, barX + Math.round(100 * Math.min(1f, progress)), barY + 2, 0xFF80FF80);
        GlStateManager.enableDepth();

        fb.unbindFramebuffer();
        fb.framebufferRender(mc.displayWidth, mc.displayHeight);
        if (screenshot && steps > 0 && step >= steps / 2) {
            screenshot = false; // development harness: one image of the screen for each reload
            File dir = new File(System.getProperty("albedo.harness"));
            new LegacyHarnessHost().screenshot(new File(dir, "reload-screen-" + System.currentTimeMillis() + ".png"));
        }
        mc.updateDisplay();
    }
}
