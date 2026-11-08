package dev.albedo.legacy;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

/**
 * The work of OptiFineRenderMixin. It finds the calls by their target and descriptor, not by the name of the calling
 * method, because each OptiFine version has different method names. Each change keeps the stack and the frames:
 * <ul>
 *   <li>the int result of a tint call and the float result of FaceBakery.getFaceBrightness (the fluid renderer reads
 *       it each frame) go through a LegacyHooks method with the same type;</li>
 *   <li>WorldRenderer.addVertexData(int[]) in BlockModelRenderer becomes the static LegacyHooks.addQuad(WorldRenderer,
 *       int[]), which takes the same stack.</li>
 * </ul>
 */
final class OptiFineRenderPatch {
    private static final String HOOKS = "dev/albedo/legacy/LegacyHooks";
    private static final String WORLD_RENDERER = "net/minecraft/client/renderer/WorldRenderer";
    private static final String COLOR_MULTIPLIER_DESC = "(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;I)I";
    private static final String FACE_BRIGHTNESS_DESC = "(Lnet/minecraft/util/EnumFacing;)F";

    private OptiFineRenderPatch() {}

    static void apply(ClassNode target) {
        boolean fluid = target.name.endsWith("/BlockFluidRenderer");
        int count = 0;
        for (MethodNode method : target.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (!fluid && isAddVertexData(call)) {
                    method.instructions.set(call, hook("addQuad", "(L" + WORLD_RENDERER + ";[I)V"));
                    count++;
                } else if (fluid && isFaceBrightness(call)) {
                    method.instructions.insert(call, hook("faceBrightness", "(F)F"));
                    count++;
                } else {
                    String name = tintHook(call, fluid);
                    if (name == null) continue;
                    method.instructions.insert(call, hook(name, "(I)I"));
                    count++;
                }
            }
        }
        String message = "OptiFine: " + count + " Albedo hooks in " + target.name;
        if (count == 0) LogManager.getLogger("Albedo").warn(message);
        else LogManager.getLogger("Albedo").info(message);
    }

    private static MethodInsnNode hook(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, desc, false);
    }

    private static boolean isAddVertexData(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals(WORLD_RENDERER) && call.desc.equals("([I)V")
                && (call.name.equals("func_178981_a") || call.name.equals("addVertexData"));
    }

    private static boolean isFaceBrightness(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.endsWith("/FaceBakery") && call.desc.equals(FACE_BRIGHTNESS_DESC)
                && (call.name.equals("func_178412_b") || call.name.equals("getFaceBrightness"));
    }

    /** @return the LegacyHooks method for the int result of this call, or null */
    private static String tintHook(MethodInsnNode call, boolean fluid) {
        boolean customColors = call.owner.equals("CustomColors") || call.owner.endsWith("/CustomColors");
        if (customColors && call.name.equals("getColorMultiplier") && call.desc.endsWith(")I")) return "optiFineBlockTint";
        if (customColors && call.name.equals("getFluidColor") && call.desc.endsWith(")I")) return "fluidTint";
        boolean colorMultiplier = call.name.equals("func_180662_a") || call.name.equals("colorMultiplier");
        if (colorMultiplier && call.desc.equals(COLOR_MULTIPLIER_DESC) && call.getOpcode() == Opcodes.INVOKEVIRTUAL) {
            return fluid ? "fluidTint" : "blockTint";
        }
        return null;
    }
}
