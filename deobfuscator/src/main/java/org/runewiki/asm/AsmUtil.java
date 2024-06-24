package org.runewiki.asm;

import java.util.*;
import java.util.function.Predicate;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class AsmUtil {
    public static AbstractInsnNode getNextReal(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && (next.getOpcode() == -1 || next.getOpcode() == 0)) {
            next = next.getNext();
        }
        return next;
    }

    public static AbstractInsnNode getPreviousReal(AbstractInsnNode insn) {
        AbstractInsnNode previous = insn.getPrevious();
        while (previous != null && (previous.getOpcode() == -1 || previous.getOpcode() == 0)) {
            previous = previous.getPrevious();
        }
        return previous;
    }

    public static boolean hasCode(MethodNode method) {
        return (method.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) == 0;
    }

    public static boolean isTerminal(AbstractInsnNode insn) {
        return insn.getOpcode() == Opcodes.GOTO ||
               insn.getOpcode() == Opcodes.RETURN ||
               insn.getOpcode() == Opcodes.ARETURN ||
               insn.getOpcode() == Opcodes.IRETURN ||
               insn.getOpcode() == Opcodes.LRETURN ||
               insn.getOpcode() == Opcodes.FRETURN ||
               insn.getOpcode() == Opcodes.DRETURN ||
               insn.getOpcode() == Opcodes.ATHROW ||
               insn.getOpcode() == Opcodes.TABLESWITCH ||
               insn.getOpcode() == Opcodes.LOOKUPSWITCH;
    }

    public static int removeIf(InsnList instructions, Predicate<AbstractInsnNode> condition) {
        AbstractInsnNode insn = instructions.getFirst();

        int count = 0;
        while (insn != null) {
            AbstractInsnNode next = insn.getNext();

            if (condition.test(insn)) {
                instructions.remove(insn);
                count++;
            }

            insn = next;
        }

        return count;
    }

    public static List<LabelNode> getJumpTargets(AbstractInsnNode insn) {
        if (insn instanceof JumpInsnNode) {
            return List.of(((JumpInsnNode) insn).label);
        }

        if (insn instanceof TableSwitchInsnNode) {
            ArrayList result = new ArrayList<LabelNode>(((TableSwitchInsnNode) insn).labels.size() + 1);
            result.addAll(((TableSwitchInsnNode) insn).labels);
            result.add(((TableSwitchInsnNode) insn).dflt);
            return result;
        }

        if (insn instanceof LookupSwitchInsnNode) {
            ArrayList result = new ArrayList<LabelNode>(((LookupSwitchInsnNode) insn).labels.size() + 1);
            result.addAll(((LookupSwitchInsnNode) insn).labels);
            result.add(((LookupSwitchInsnNode) insn).dflt);
            return result;
        }

        return List.of();
    }

}
