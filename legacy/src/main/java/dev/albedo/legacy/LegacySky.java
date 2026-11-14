package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.sky.SkyDayTime;
import dev.albedo.core.sky.SkyDefer;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyLight;
import dev.albedo.core.sky.SkyMesh;
import dev.albedo.core.sky.SkyTurn;
import dev.albedo.legacy.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.FloatBuffer;

/**
 * Draws the custom sky on a sphere in place of the legacy sky disc, with {@link LegacySharpSky} when it can, else from
 * one texture of a fixed size that is uploaded on the render thread only when the core has a new image, as the twin of
 * modern {@code FixedSky}: a fix lands in both, with the steps in the same order.
 */
public final class LegacySky {
    private static final float RADIUS = 100f;
    private static final SkyDayTime DAY_TIME = new SkyDayTime();
    private static DynamicTexture texture;
    private static long uploaded = -1;
    private static int sphere;
    /** The matrix of a turning sky for the GL, which each frame fills again. */
    private static final FloatBuffer TURN = BufferUtils.createFloatBuffer(16);
    /** The sharp sky of the sky pass that waits for the clouds or the solid world. */
    private static final class Waiting {
        final SkyTurn turn;
        final LegacySharpSky.Prepared sharp;
        final float brightness;

        Waiting(SkyTurn turn, LegacySharpSky.Prepared sharp, float brightness) {
            this.turn = turn;
            this.sharp = sharp;
            this.brightness = brightness;
        }
    }

    private static final SkyDefer<Waiting> DEFER = new SkyDefer<Waiting>();
    /** True in a frame where the fog of water, lava or blindness hides the sky, so that the fogged vanilla sky draws. */
    private static boolean fogHidesSky;

    private LegacySky() {}

    /** Starts a frame, which forgets the sharp sky of the last frame that still waits and reads the fog of the camera. */
    public static void frameStart(float partialTicks) {
        DEFER.frameStart();
        fogHidesSky = Game.specialFog(partialTicks);
    }

    /** The custom sky to draw, or null, also when OptiFine does not draw the sky (Sky off or a shader pack) or the fog hides the sky. */
    public static SkyImage image() {
        SkyImage img = Albedo.skyImage();
        return img == null || fogHidesSky || !LegacySkyPatch.hooked() || !OptiFine.drawsSky() ? null : img;
    }

    public static boolean active() {
        return image() != null;
    }

    public static boolean hidesStars() {
        SkyImage img = image();
        return img != null && img.design().celestial() == SkyDesign.Celestial.HIDE;
    }

    /** The factor of the sky and fog colors for the time of day and the weather. */
    static float brightness(SkyImage img) {
        World world = Game.world();
        if (world == null) return 1f;
        return SkyLight.of(img.design(), world.getWorldTime(), world.getRainStrength(1f), world.getThunderStrength(1f));
    }

    /** The fog color {r, g, b} near the horizon where the camera looks, or null without a custom sky. */
    static float[] fogRgb() {
        SkyImage img = image();
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        World world = Game.world();
        if (img == null || view == null || world == null || !world.provider.isSurfaceWorld()) return null;
        return img.fogRgb(turn(img), view.rotationYaw, brightness(img));
    }

    /** Records the day time at the end of each client tick, for the frames until the next tick. */
    static void tick() {
        World world = Game.world();
        if (world != null) DAY_TIME.tick(world.getWorldTime());
    }

    /** The rotation of the custom sky in this frame, for the sky sphere and the fog. */
    private static SkyTurn turn(SkyImage img) {
        Minecraft mc = Minecraft.getMinecraft();
        float partialTick = ((MinecraftAccessor) mc).albedo$timer().renderPartialTicks;
        return SkyTurn.at(img.design().turning(), Game.world() == null ? 0 : DAY_TIME.at(partialTick));
    }

    /**
     * Draws the sky where vanilla draws its disc, which has texturing off and the sky color set.
     *
     * @return false when there is no custom sky, so that the vanilla disc draws
     */
    private static boolean draw() {
        SkyImage img = image();
        if (img == null) return false;
        prepare(img);
        float k = brightness(img);
        SkyTurn turn = turn(img);
        LegacySharpSky.Prepared sharp = LegacySharpSky.prepare(img, turn);
        if (sharp != null) {
            drawBlack();
            Waiting waiting = new Waiting(turn, sharp, k);
            if (!DEFER.defer(waiting)) drawSharp(waiting);
            return true;
        }
        GlStateManager.pushMatrix();
        turnMatrix(turn);
        GlStateManager.enableTexture2D();
        GlStateManager.disableFog();
        GlStateManager.disableCull();
        GlStateManager.color(k, k, k, 1f);
        GlStateManager.bindTexture(texture.getGlTextureId());
        GlStateManager.callList(sphere);
        GlStateManager.enableCull();
        GlStateManager.enableFog();
        GlStateManager.disableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
        return true;
    }

    /** Multiplies the model view matrix of the GL by the matrix of a sky that turns. */
    private static void turnMatrix(SkyTurn turn) {
        if (turn.still()) return;
        TURN.clear();
        TURN.put(turn.matrix()).flip();
        GL11.glMultMatrix(TURN);
    }

    /**
     * The sharp sky adds its color to the frame, so this draws black below it on the sphere at the far depth with no blend,
     * texture or fog, only on the pixels that still have the far depth of the clear, and leaves the depth test on as the
     * sharp sky does.
     */
    private static void drawBlack() {
        GlStateManager.disableTexture2D();
        GlStateManager.disableFog();
        GlStateManager.disableBlend();
        GlStateManager.disableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(false);
        GlStateManager.color(0f, 0f, 0f, 1f);
        // The game never changes the depth range, so the sphere gets the far depth here and the range goes back to the full range after it.
        GL11.glDepthRange(1.0, 1.0);
        GlStateManager.callList(sphere);
        GL11.glDepthRange(0.0, 1.0);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableCull();
        GlStateManager.enableFog();
    }

    private static void drawSharp(Waiting sky) {
        GlStateManager.pushMatrix();
        turnMatrix(sky.turn);
        LegacySharpSky.draw(sky.sharp, sphere, sky.brightness);
        GlStateManager.popMatrix();
    }

    /** Draws the sharp sky that waits, before the clouds or after the solid world, whose camera matrix is that of the sky. */
    public static void drawWaiting() {
        Waiting waiting = DEFER.take();
        if (waiting == null) return;
        drawSharp(waiting);
        GlStateManager.depthMask(true);
        // The sky shader left its term texture on the unit of the block textures.
        Minecraft.getMinecraft().getTextureManager().bindTexture(Game.blocksTexture());
    }

    private static void prepare(SkyImage img) {
        ensureTexture(img);
        ensureSphere();
    }

    private static void ensureTexture(SkyImage img) {
        if (texture == null || texture.getTextureData().length != img.argb().length) {
            if (texture != null) texture.deleteGlTexture();
            texture = new DynamicTexture(img.width(), img.height());
            uploaded = -1;
        }
        if (uploaded != img.serial()) {
            System.arraycopy(img.argb(), 0, texture.getTextureData(), 0, img.argb().length);
            texture.updateDynamicTexture();
            // The upload sets nearest filtering and repeat in both directions.
            GlStateManager.bindTexture(texture.getGlTextureId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            uploaded = img.serial();
        }
    }

    private static void ensureSphere() {
        if (sphere != 0) return;
        sphere = GLAllocation.generateDisplayLists(1);
        GL11.glNewList(sphere, GL11.GL_COMPILE);
        Game.drawTriangles(SkyMesh.sphere(RADIUS));
        GL11.glEndList();
    }

    /** Draws the sky disc of renderSky, or the custom sky in its place. */
    public static void drawSky(VertexBuffer buffer, int mode) {
        // The custom sky draws from client memory, which GL refuses while the vanilla sky buffer is bound.
        buffer.unbindBuffer();
        boolean drawn = draw();
        buffer.bindBuffer();
        if (!drawn) buffer.drawArrays(mode);
    }

    public static void drawSky(int list) {
        if (!draw()) GlStateManager.callList(list);
    }

    public static void drawStars(VertexBuffer buffer, int mode) {
        if (!hidesStars()) buffer.drawArrays(mode);
    }

    public static void drawStars(int list) {
        if (!hidesStars()) GlStateManager.callList(list);
    }

    /** Draws a dark disc below the horizon, which the custom sky leaves out. */
    public static void drawDark(VertexBuffer buffer, int mode) {
        if (!active()) buffer.drawArrays(mode);
    }

    public static void drawDark(int list) {
        if (!active()) GlStateManager.callList(list);
    }

    /** Draws the sunrise fan or the box below the horizon, or ends the mesh when the custom sky leaves it out. */
    public static void drawMesh(Tessellator tessellator) {
        if (!active()) {
            tessellator.draw();
            return;
        }
        Game.discardMesh(tessellator);
    }

    /** Whether the sky of an OptiFine resource pack hides the stars, which it does not when the custom sky replaces it. */
    public static boolean customSkyLayers(boolean layers) {
        return layers && !active();
    }
}
