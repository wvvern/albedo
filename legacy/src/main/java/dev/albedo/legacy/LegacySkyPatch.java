package dev.albedo.legacy;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldInsnNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends the draws of RenderGlobal.renderSky through LegacySky, found by the field that each draw reads and not by
 * their order, so that the same patch works with and without OptiFine, which adds draws to this method.
 */
final class LegacySkyPatch {
    /** A part of the vanilla sky, found by the fields of its vertex buffer and its display list. */
    private enum Part {
        SKY("drawSky", "skyVBO", "field_175012_t", "glSkyList", "field_72771_w"),
        STARS("drawStars", "starVBO", "field_175013_s", "starGLCallList", "field_72772_v"),
        DARK("drawDark", "sky2VBO", "field_175011_u", "glSkyList2", "field_72781_x");

        final String hook;
        final String[] fields;

        Part(String hook, String... fields) {
            this.hook = hook;
            this.fields = fields;
        }

        static Part of(String field) {
            for (Part part : values()) for (String name : part.fields) if (name.equals(field)) return part;
            return null;
        }
    }

    private static final String SKY = "dev/albedo/legacy/LegacySky";
    private static final String RENDER_GLOBAL = "net/minecraft/client/renderer/RenderGlobal";
    private static final String VERTEX_BUFFER = "net/minecraft/client/renderer/vertex/VertexBuffer";
    private static final String TESSELLATOR = "net/minecraft/client/renderer/Tessellator";
    private static final String CUSTOM_SKY_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/client/renderer/texture/TextureManager;F)V";

    /** True when the patch found the sky draw, so that LegacySky can draw the custom sky. */
    private static volatile boolean skyHooked;
    /** The OptiFine class that draws the sky of a resource pack, with dots, or null. */
    private static volatile String customSkyOwner;

    private LegacySkyPatch() {}

    static boolean skyHooked() {
        return skyHooked;
    }

    static String customSkyOwner() {
        return customSkyOwner;
    }

    /** Replaces each found call with a LegacySky method that takes the same stack, so that the frames stay valid. */
    static void apply(ClassNode target) {
        int count = 0;
        for (MethodNode method : target.methods) {
            if (!method.desc.equals("(FI)V") || !(method.name.equals("renderSky") || method.name.equals("func_174976_a"))) continue;
            List<MethodInsnNode> meshes = new ArrayList<>();
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (isTessellatorDraw(call)) {
                    meshes.add(call);
                } else if (isDrawArrays(call) || isCallList(call)) {
                    Part part = Part.of(fieldBefore(call));
                    if (part == null) continue;
                    String desc = call.getOpcode() == Opcodes.INVOKESTATIC ? "(I)V" : "(L" + VERTEX_BUFFER + ";I)V";
                    method.instructions.set(call, hook(part.hook, desc));
                    if (part == Part.SKY) skyHooked = true;
                    count++;
                } else if (isCustomSky(call, "renderSky", CUSTOM_SKY_DESC)) {
                    customSkyOwner = call.owner.replace('/', '.');
                    method.instructions.set(call, hook("drawCustomSky", CUSTOM_SKY_DESC));
                    count++;
                } else if (isCustomSky(call, "hasSkyLayers", "(Lnet/minecraft/world/World;)Z")) {
                    method.instructions.insert(call, hook("customSkyLayers", "(Z)Z"));
                    count++;
                }
            }
            // The first mesh is the sunrise fan and the last is the box below the horizon; the others are the sun and moon.
            if (meshes.size() >= 2) {
                method.instructions.set(meshes.get(0), hook("drawMesh", "(L" + TESSELLATOR + ";)V"));
                method.instructions.set(meshes.get(meshes.size() - 1), hook("drawMesh", "(L" + TESSELLATOR + ";)V"));
                count += 2;
            }
        }
        String message = count + " Albedo sky hooks in " + target.name;
        if (skyHooked) LogManager.getLogger("Albedo").info(message);
        else LogManager.getLogger("Albedo").warn(message + ", so the custom sky is off");
    }

    private static MethodInsnNode hook(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, SKY, name, desc, false);
    }

    /** @return the name of the RenderGlobal field that the call reads in the two instructions before it, or null */
    private static String fieldBefore(MethodInsnNode call) {
        AbstractInsnNode insn = call.getPrevious();
        for (int steps = 0; insn != null && steps < 2; insn = insn.getPrevious()) {
            if (insn.getOpcode() < 0) continue;
            if (insn.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) insn).owner.equals(RENDER_GLOBAL)) return ((FieldInsnNode) insn).name;
            steps++;
        }
        return null;
    }

    private static boolean isDrawArrays(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals(VERTEX_BUFFER) && call.desc.equals("(I)V")
                && (call.name.equals("drawArrays") || call.name.equals("func_177358_a"));
    }

    private static boolean isCallList(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals("net/minecraft/client/renderer/GlStateManager")
                && call.desc.equals("(I)V") && (call.name.equals("callList") || call.name.equals("func_179148_o"));
    }

    private static boolean isTessellatorDraw(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals(TESSELLATOR) && call.desc.equals("()V")
                && (call.name.equals("draw") || call.name.equals("func_78381_a"));
    }

    /** OptiFine's CustomSky, in the default package or in net/optifine, which draws the sky of a resource pack. */
    private static boolean isCustomSky(MethodInsnNode call, String name, String desc) {
        boolean owner = call.owner.equals("CustomSky") || call.owner.endsWith("/CustomSky");
        return call.getOpcode() == Opcodes.INVOKESTATIC && owner && call.name.equals(name) && call.desc.equals(desc);
    }
}
