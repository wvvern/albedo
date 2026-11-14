package dev.albedo.legacy;

import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldInsnNode;
import org.spongepowered.asm.lib.tree.FrameNode;
import org.spongepowered.asm.lib.tree.InsnList;
import org.spongepowered.asm.lib.tree.InsnNode;
import org.spongepowered.asm.lib.tree.JumpInsnNode;
import org.spongepowered.asm.lib.tree.LabelNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.lib.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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
    private static final String GL_STATE_MANAGER = "net/minecraft/client/renderer/GlStateManager";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String TEXTURE_MANAGER = "net/minecraft/client/renderer/texture/TextureManager";
    private static final String CUSTOM_SKY_DESC = "(L" + WORLD + ";L" + TEXTURE_MANAGER + ";F)V";
    private static final String CUSTOM_SKY_BRIDGE = "albedo$customSky";
    /** The sunrise fan, the sun, the moon and the box below the horizon, in this order in vanilla and in OptiFine. */
    private static final int MESHES = 4;

    /** True when the patch found all parts of the sky, so that LegacySky can draw the custom sky. */
    private static volatile boolean hooked;

    private LegacySkyPatch() {}

    static boolean hooked() {
        return hooked;
    }

    /** Replaces each found call with a LegacySky method that takes the same stack, so that the frames stay valid. */
    static void apply(ClassNode target) {
        Set<Part> found = EnumSet.noneOf(Part.class);
        List<MethodInsnNode> meshes = new ArrayList<>();
        String customSky = null;
        for (MethodNode method : target.methods) {
            if (!method.desc.equals("(FI)V") || !(method.name.equals("renderSky") || method.name.equals("func_174976_a"))) continue;
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
                    found.add(part);
                } else if (isCustomSky(call, "renderSky", CUSTOM_SKY_DESC)) {
                    customSky = call.owner;
                    method.instructions.set(call, AsmCalls.invokeStatic(target.name, CUSTOM_SKY_BRIDGE, CUSTOM_SKY_DESC));
                } else if (isCustomSky(call, "hasSkyLayers", "(L" + WORLD + ";)Z")) {
                    method.instructions.insert(call, hook("customSkyLayers", "(Z)Z"));
                }
            }
            if (meshes.size() == MESHES) {
                String desc = "(L" + TESSELLATOR + ";)V";
                method.instructions.set(meshes.get(0), hook("drawMesh", desc));
                method.instructions.set(meshes.get(MESHES - 1), hook("drawMesh", desc));
            }
        }
        if (customSky != null) target.methods.add(customSkyBridge(customSky));
        hooked = found.size() == Part.values().length && meshes.size() == MESHES;
        String message = "Sky hooks in " + target.name + ": " + found + ", " + meshes.size() + " meshes, OptiFine custom sky " + (customSky != null);
        if (hooked) AsmCalls.LOG.info(message);
        else AsmCalls.LOG.warn(message + "; the custom sky is off, because renderSky is not as expected");
    }

    /** A static method that calls OptiFine's CustomSky.renderSky only when the custom sky does not replace it. */
    private static MethodNode customSkyBridge(String owner) {
        MethodNode bridge = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, CUSTOM_SKY_BRIDGE,
                CUSTOM_SKY_DESC, null, null);
        LabelNode end = new LabelNode();
        InsnList code = bridge.instructions;
        code.add(hook("active", "()Z"));
        code.add(new JumpInsnNode(Opcodes.IFNE, end));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new VarInsnNode(Opcodes.FLOAD, 2));
        code.add(AsmCalls.invokeStatic(owner, "renderSky", CUSTOM_SKY_DESC));
        code.add(end);
        code.add(new FrameNode(Opcodes.F_NEW, 3, new Object[]{WORLD, TEXTURE_MANAGER, Opcodes.FLOAT}, 0, new Object[0]));
        code.add(new InsnNode(Opcodes.RETURN));
        bridge.maxStack = 3;
        bridge.maxLocals = 3;
        return bridge;
    }

    private static MethodInsnNode hook(String name, String desc) {
        return AsmCalls.invokeStatic(SKY, name, desc);
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
        return AsmCalls.is(call, Opcodes.INVOKEVIRTUAL, VERTEX_BUFFER, "(I)V", "drawArrays", "func_177358_a");
    }

    private static boolean isCallList(MethodInsnNode call) {
        return AsmCalls.is(call, Opcodes.INVOKESTATIC, GL_STATE_MANAGER, "(I)V", "callList", "func_179148_o");
    }

    private static boolean isTessellatorDraw(MethodInsnNode call) {
        return AsmCalls.is(call, Opcodes.INVOKEVIRTUAL, TESSELLATOR, "()V", "draw", "func_78381_a");
    }

    /** OptiFine's CustomSky, in the default package or in net/optifine, which draws the sky of a resource pack. */
    private static boolean isCustomSky(MethodInsnNode call, String name, String desc) {
        boolean owner = call.owner.equals("CustomSky") || call.owner.endsWith("/CustomSky");
        return call.getOpcode() == Opcodes.INVOKESTATIC && owner && call.name.equals(name) && call.desc.equals(desc);
    }
}
