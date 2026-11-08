package dev.blockpainter.legacy;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

/**
 * The work of OptiFineRenderMixin. It finds the tint calls and the face brightness by their target and descriptor,
 * not by the name of the calling method, because each OptiFine version has different method names. It only sends an
 * int or float result through a LegacyHooks method with the same type, so the stack and the frames do not change.
 */
final class OptiFineRenderPatch {
    private static final String HOOKS = "dev/blockpainter/legacy/LegacyHooks";
    private static final String COLOR_MULTIPLIER_DESC = "(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;I)I";
    private static final String FACE_BRIGHTNESS_DESC = "(Lnet/minecraft/util/EnumFacing;)F";

    private OptiFineRenderPatch() {}

    static void apply(ClassNode target) {
        boolean faceBakery = target.name.endsWith("/FaceBakery");
        boolean fluid = target.name.endsWith("/BlockFluidRenderer");
        int count = 0;
        for (MethodNode method : target.methods) {
            boolean faceBrightness = faceBakery && FACE_BRIGHTNESS_DESC.equals(method.desc)
                    && (method.name.equals("func_178412_b") || method.name.equals("getFaceBrightness"));
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (faceBrightness && insn.getOpcode() == Opcodes.FRETURN) {
                    method.instructions.insertBefore(insn, hook("faceBrightness", "(F)F"));
                    count++;
                } else if (!faceBakery && insn instanceof MethodInsnNode) {
                    String name = tintHook((MethodInsnNode) insn, fluid);
                    if (name == null) continue;
                    method.instructions.insert(insn, hook(name, "(I)I"));
                    count++;
                }
            }
        }
        String message = "OptiFine: " + count + " Block Painter hooks in " + target.name;
        if (count == 0) LogManager.getLogger("Block Painter").warn(message);
        else LogManager.getLogger("Block Painter").info(message);
    }

    private static MethodInsnNode hook(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, desc, false);
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
