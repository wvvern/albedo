package dev.albedo.legacy;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.util.List;

/** The game members whose names are different on 1.8.9 and 1.12.2, with the same static methods in each version folder. */
public final class Game {
    private Game() {}

    public static WorldClient world() {
        return Minecraft.getMinecraft().world;
    }

    public static EntityPlayerSP player() {
        return Minecraft.getMinecraft().player;
    }

    public static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    public static File gameDir() {
        return Minecraft.getMinecraft().gameDir;
    }

    public static int floor(double value) {
        return MathHelper.floor(value);
    }

    public static ResourceLocation blocksTexture() {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }

    /** Shows a message above the hotbar, where the game shows the name of a record. */
    public static void showMessage(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI != null) mc.ingameGUI.setOverlayMessage(message, false);
    }

    /** Draws triangles of 5 floats for each vertex (x, y, z, u, v) with the tessellator of the game. */
    public static void drawTriangles(float[] v) {
        BufferBuilder r = Tessellator.getInstance().getBuffer();
        r.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);
        for (int i = 0; i < v.length; i += 5) r.pos(v[i], v[i + 1], v[i + 2]).tex(v[i + 3], v[i + 4]).endVertex();
        Tessellator.getInstance().draw();
    }

    /** Adds a quad of the chunk mesh with no face shade when shading is off, so that a shading change needs no model reload. */
    public static void addQuad(BufferBuilder buffer, int[] vertexData) {
        buffer.addVertexData(LegacyHooks.quad(vertexData));
    }

    /** Ends the mesh of the tessellator with no draw. */
    public static void discardMesh(Tessellator tessellator) {
        tessellator.getBuffer().finishDrawing();
        tessellator.getBuffer().reset();
    }

    /** Draws the dark background of the option screens on a screen of this size in GUI units. */
    public static void drawBackground(int w, int h) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(Gui.OPTIONS_BACKGROUND);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder wr = tessellator.getBuffer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        wr.pos(0, h, 0).tex(0, h / 32.0).color(64, 64, 64, 255).endVertex();
        wr.pos(w, h, 0).tex(w / 32.0, h / 32.0).color(64, 64, 64, 255).endVertex();
        wr.pos(w, 0, 0).tex(w / 32.0, 0).color(64, 64, 64, 255).endVertex();
        wr.pos(0, 0, 0).tex(0, 0).color(64, 64, 64, 255).endVertex();
        tessellator.draw();
    }

    /** True when the fog code of the game gives the camera the fog of water, lava or blindness, which hides the sky. */
    public static boolean specialFog(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null || mc.world == null) return false;
        Material material = ActiveRenderInfo.getBlockStateAtEntityViewpoint(mc.world, view, partialTicks).getMaterial();
        if (material == Material.WATER || material == Material.LAVA) return true;
        return view instanceof EntityLivingBase && ((EntityLivingBase) view).isPotionActive(MobEffects.BLINDNESS);
    }

    /** Opens the save with this name, or makes it as a flat creative world with commands. */
    public static void openFlatWorld(String name) {
        WorldSettings settings = new WorldSettings(42L, GameType.CREATIVE, false, false, WorldType.FLAT).enableCommands();
        Minecraft.getMinecraft().launchIntegratedServer(name, name, settings);
    }

    public static List<EntityPlayerMP> players(MinecraftServer server) {
        return server.getPlayerList().getPlayers();
    }

    public static void mute() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.getSoundLevel(SoundCategory.MASTER) != 0f) mc.gameSettings.setSoundLevel(SoundCategory.MASTER, 0f);
    }
}
