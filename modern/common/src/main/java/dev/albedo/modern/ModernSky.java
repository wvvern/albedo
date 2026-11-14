package dev.albedo.modern;

import dev.albedo.core.Albedo;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.sky.SkyDayTime;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyLight;
import dev.albedo.core.sky.SkyTurn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=1.21.6 {
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
//?}

/**
 * The version-independent parts of the custom sky. {@link #frameStart} calls the family class: {@code FixedSky},
 * {@code GlSky}, {@code PrecompiledSky} or {@code PearlSky}.
 */
public final class ModernSky {
    static final float RADIUS = 100f;
    private static final SkyDayTime DAY_TIME = new SkyDayTime();
    private static java.lang.reflect.Method irisInUse;
    private static Object iris;
    private static boolean irisLooked;
    /** True when the last sky disc of a sky pass was the custom sky, which the later parts of the same pass read. */
    private static boolean replacedDisc;

    private ModernSky() {}

    /** Starts a frame, which forgets the sharp sky of the last frame that still waits. */
    static void frameStart() {
        //? if gl_fixed {
        /*FixedSky.frameStart();
        *///?}
        //? if >=26.3 {
        PearlSky.frameStart();
        //?} elif >=1.21.6 {
        /*PrecompiledSky.frameStart();
        *///?} elif gl_sky {
        /*GlSky.frameStart();
        *///?}
    }

    /** True when the custom sky replaces the vanilla sky disc in this frame. */
    public static boolean active() {
        return Albedo.skyImage() != null && !vanillaWithPack();
    }

    public static boolean hidesStars() {
        SkyImage img = Albedo.skyImage();
        return img != null && !vanillaWithPack() && img.design().celestial() == SkyDesign.Celestial.HIDE;
    }

    /** Records whether the custom sky drew in place of the sky disc of this sky pass; the families with a disc call this. */
    static void discDrawn(boolean replaced) {
        replacedDisc = replaced;
    }

    /** True when the custom sky drew in place of the sky disc of this sky pass, so the dark lower disc and the sunrise fan must not draw. */
    public static boolean replacedDisc() {
        return replacedDisc;
    }

    /** True when the custom sky drew in this sky pass and hides the vanilla stars. */
    public static boolean hidesStarsThisPass() {
        return replacedDisc && hidesStars();
    }

    /** True when the image draws with the sky shader in this frame: the design and the setting permit it, and no shader pack draws the sky. */
    static boolean wantsSharp(SkyImage img) {
        return img.drawsSharp() && !shaderPackInUse();
    }

    /** True when the vanilla sky draws in place of the custom sky, because the GL families do not draw it with a shader pack. */
    static boolean vanillaWithPack() {
        //? if gl_sky {
        /*return shaderPackInUse();
        *///?} else {
        return false;
        //?}
    }

    /** Iris with a shader pack draws the sky in its own way, so the sky then draws from the baked texture, or stays vanilla in the GL families. */
    static boolean shaderPackInUse() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                iris = null;
            }
        }
        if (iris == null) return false;
        try {
            return (Boolean) irisInUse.invoke(iris);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** The factor of the sky and fog colors for the time of day and the weather, 1 for a sky that stays bright. */
    static float brightness(SkyImage img) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 1f : SkyLight.of(img.design(), dayTime(level), level.getRainLevel(1f), level.getThunderLevel(1f));
    }

    /** The day time of the world in ticks with no wrap, where 0 is sunrise. */
    private static long dayTime(ClientLevel level) {
        //? if >=26.1 {
        return level.getOverworldClockTime();
        //?} else {
        /*return level.getDayTime();
        *///?}
    }

    /** Records the day time at the end of each client tick, for the frames until the next tick. */
    static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) DAY_TIME.tick(dayTime(level));
    }

    /** The rotation of the custom sky in this frame, for the sky sphere and the fog. */
    static SkyTurn turn(SkyImage img) {
        ClientLevel level = Minecraft.getInstance().level;
        return SkyTurn.at(img.design().turning(), level == null ? 0 : DAY_TIME.at(partialTick()));
    }

    /** The part of a tick from the last tick to this frame. */
    private static float partialTick() {
        //? if >=1.21.2 {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        //?} elif >=1.21 {
        /*return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        *///?} else {
        /*return Minecraft.getInstance().getFrameTime();
        *///?}
    }

    /** The camera yaw in degrees, where 0 looks south. */
    static float cameraYaw() {
        //? if >=26.2 {
        return Minecraft.getInstance().gameRenderer.mainCamera().yRot();
        //?} elif >=1.21.11 {
        /*return Minecraft.getInstance().gameRenderer.getMainCamera().yRot();
        *///?} else {
        /*return Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
        *///?}
    }

    //? if >=1.21.6 {
    /** A copy of the model view matrix of the sky sphere, turned for a sky that turns. */
    static Matrix4f turned(Matrix4fc modelView, SkyImage img) {
        SkyTurn turn = turn(img);
        Matrix4f out = new Matrix4f(modelView);
        return turn.still() ? out : out.mul(new Matrix4f().set(turn.matrix()));
    }
    //?}
}
