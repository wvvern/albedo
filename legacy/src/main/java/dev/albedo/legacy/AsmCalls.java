package dev.albedo.legacy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.MethodInsnNode;

/** The call matching and the call making of the ASM patches. */
final class AsmCalls {
    static final Logger LOG = LogManager.getLogger("albedo");

    private AsmCalls() {}

    /** True when the call has this opcode, owner and descriptor, and the MCP or the SRG name. */
    static boolean is(MethodInsnNode call, int opcode, String owner, String desc, String mcpName, String srgName) {
        return call.getOpcode() == opcode && call.owner.equals(owner) && call.desc.equals(desc)
                && (call.name.equals(mcpName) || call.name.equals(srgName));
    }

    static MethodInsnNode invokeStatic(String owner, String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, desc, false);
    }
}
