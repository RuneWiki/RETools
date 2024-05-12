package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.runewiki.asm.AsmUtil;
import org.runewiki.asm.InsnMatcher;
import org.runewiki.asm.transform.Transformer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Rewrite monitor instructions for Fernflower compatibility
 */
public class MonitorTransformer extends Transformer {
    // relies on JsrInliner rewriting RET into GOTO
    private final InsnMatcher JSR_MATCHER = InsnMatcher.compile("ACONST_NULL GOTO");
    private final InsnMatcher SUBROUTINE_MATCHER = InsnMatcher.compile("ASTORE ALOAD MONITOREXIT GOTO");

    private final InsnMatcher LOAD_MATCHER = InsnMatcher.compile("ASTORE ALOAD MONITORENTER");

    private int subroutinesInlined = 0;
    private int tryRangesExtended = 0;
    private int loadsReplaced = 0;

    @Override
    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        inlineSubroutines(method);
        extendTryRanges(method);
        replaceLoadWithDup(method);
        return false;
    }

    private void inlineSubroutines(MethodNode method) {
        Map<AbstractInsnNode, List<AbstractInsnNode>> subroutines = new HashMap<>();
        for (List<AbstractInsnNode> match : this.SUBROUTINE_MATCHER.match(method.instructions)) {
            subroutines.put(match.get(0), match);
        }

        for (List<AbstractInsnNode> match : this.JSR_MATCHER.match(method.instructions)) {
            JumpInsnNode jsr = (JumpInsnNode) match.get(1);
            List<AbstractInsnNode> subroutine = subroutines.get(AsmUtil.getNextReal(jsr.label));
            if (subroutine == null) {
                continue;
            }

            JumpInsnNode ret = (JumpInsnNode) subroutine.get(3);
            if (AsmUtil.getNextReal(ret.label) != AsmUtil.getNextReal(jsr)) {
                continue;
            }

            Map<LabelNode, LabelNode> clonedLabels = new HashMap<>();
            method.instructions.set(match.get(0), subroutine.get(1).clone(clonedLabels));
            method.instructions.set(match.get(1), subroutine.get(2).clone(clonedLabels));

            subroutinesInlined++;
        }
    }

    private void extendTryRanges(MethodNode method) {
        for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
            if (tryCatch.type != null) {
                continue;
            }

            AbstractInsnNode monitorenter = AsmUtil.getPreviousReal(tryCatch.start);
            if (monitorenter.getOpcode() != Opcodes.MONITORENTER) {
                continue;
            }

            AbstractInsnNode aload = AsmUtil.getNextReal(tryCatch.end);
            if (aload.getOpcode() != Opcodes.ALOAD) {
                continue;
            }

            AbstractInsnNode monitorexit = AsmUtil.getNextReal(aload);
            if (monitorexit.getOpcode() != Opcodes.MONITOREXIT) {
                continue;
            }

            AbstractInsnNode end = AsmUtil.getNextReal(monitorexit.getNext());
            if (end == null) {
                continue;
            }

            LabelNode label = new LabelNode();
            method.instructions.insertBefore(end, label);
            tryCatch.end = label;

            tryRangesExtended++;
        }
    }

    private void replaceLoadWithDup(MethodNode method) {
        List<List<AbstractInsnNode>> matches = this.LOAD_MATCHER.match(method);

        for (List<AbstractInsnNode> match : matches) {
            method.instructions.insertBefore(match.get(0), new InsnNode(Opcodes.DUP));
            method.instructions.remove(match.get(1));

            loadsReplaced++;
        }
    }

    @Override
    public void postTransform(List<ClassNode> classes) {
        System.out.println("Inlined " + subroutinesInlined + " MONITOREXIT subroutines");
        System.out.println("Extended " + tryRangesExtended + " try ranges to cover MONITOREXIT instructions");
        System.out.println("Replaced " + loadsReplaced + " ASTORE ALOAD sequences with DUP ASTORE");
    }
}
