package dev.albedo.legacy;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

/**
 * The work of OptiFineRenderMixin, which finds the calls by their target and descriptor, not by the name of the
 * calling method, because each OptiFine version has different method names.
 */
final class OptiFineRenderPatch {
    private static final String HOOKS = "dev/albedo/legacy/LegacyHooks";
    private static final String GAME = "dev/albedo/legacy/Game";
    private static final String WORLD_RENDERER = "net/minecraft/client/renderer/WorldRenderer";
    private static final String COLOR_MULTIPLIER_DESC = "(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;I)I";
    private static final String FACE_BRIGHTNESS_DESC = "(Lnet/minecraft/util/EnumFacing;)F";

    private OptiFineRenderPatch() {}

    /**
     * Sends each found call through a LegacyHooks or Game method that takes the same stack and gives the same type, so that
     * the frames stay valid.
     */
    static void apply(ClassNode target) {
        boolean fluid = target.name.endsWith("/BlockFluidRenderer");
        int count = 0;
        for (MethodNode method : target.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (!fluid && isAddVertexData(call)) {
                    method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, GAME, "addQuad", "(L" + WORLD_RENDERER + ";[I)V", false));
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
        if (count == 0) LogManager.getLogger("albedo").warn(message);
        else LogManager.getLogger("albedo").info(message);
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
