package org.runewiki.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class AsmUtil {
    public static AbstractInsnNode getNextReal(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && (next.getOpcode() == -1 || next.getOpcode() == 0)) {
            next = next.getNext();
        }
        return next;
    }

    public static boolean hasCode(MethodNode method) {
        return (method.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) == 0;
    }
}
