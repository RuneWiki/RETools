package rs.lostcity.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

public class InsnNodeUtil {
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

    public static AbstractInsnNode toAbstractInsnNode(int i) {
        if (i == -1) return new InsnNode(Opcodes.ICONST_M1);
        if (i == 0) return new InsnNode(Opcodes.ICONST_0);
        if (i == 1) return new InsnNode(Opcodes.ICONST_1);
        if (i == 2) return new InsnNode(Opcodes.ICONST_2);
        if (i == 3) return new InsnNode(Opcodes.ICONST_3);
        if (i == 4) return new InsnNode(Opcodes.ICONST_4);
        if (i == 5) return new InsnNode(Opcodes.ICONST_5);
        if (i > Byte.MIN_VALUE && i < Byte.MAX_VALUE) return new IntInsnNode(Opcodes.BIPUSH, i);
        if (i > Short.MIN_VALUE && i < Short.MAX_VALUE) return new IntInsnNode(Opcodes.SIPUSH, i);
        return new LdcInsnNode(i);
    }

    public static boolean isBodyEmpty(TryCatchBlockNode tryCatch) {
        var current = tryCatch.start.getNext();

        while (true) {
            if (current == null) {
                System.err.println("Failed to reach end of try-catch block.");
                return false;
            } else if (current == tryCatch.end) {
                return true;
            } else if (current.getOpcode() != -1) {
                return false;
            } else {
                current = current.getNext();
            }
        }
    }

    // actually MethodNodeUtil :)
    public static void removeDeadCode(MethodNode method, String owner) {
        boolean changed;
        do {
            changed = false;

            try {
                var analyzer = new Analyzer(new BasicInterpreter());
                var frames = analyzer.analyze(owner, method);

                int i = 0;
                for (var insn : method.instructions) {
                    if (frames[i++] != null || insn instanceof LabelNode) {
                        continue;
                    }

                    method.instructions.remove(insn);
                    changed = true;
                }

                changed = changed || method.tryCatchBlocks.removeIf(tryCatch -> isBodyEmpty(tryCatch));
            } catch (AnalyzerException ignore) {
            }
        } while (changed);
    }
}
