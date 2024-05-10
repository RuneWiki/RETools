package org.runewiki.asm;

import org.objectweb.asm.tree.AbstractInsnNode;

public class AsmUtil {
    public static AbstractInsnNode getNextReal(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && (next.getOpcode() == -1 || next.getOpcode() == 0)) {
            next = next.getNext();
        }
        return next;
    }
}
