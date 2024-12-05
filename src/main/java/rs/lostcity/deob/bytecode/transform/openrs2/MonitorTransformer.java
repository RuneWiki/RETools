package rs.lostcity.deob.bytecode.transform.openrs2;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import rs.lostcity.asm.InsnMatcher;
import rs.lostcity.asm.InsnNodeUtil;
import rs.lostcity.asm.transform.Transformer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A [Transformer] that rewrites `synchronized` blocks produced by older
 * versions of the Java compiler (1.3 and older) into a more modern format.
 * This is required for compatibility with Fernflower, which does not
 * understand the older format.
 *
 * This transformer depends on [JSRInlinerAdapter].
 *
 * It makes three changes:
 *
 * - Inlines `MONITOREXIT` subroutines. [JSRInlinerAdapter] only replaces
 *   `JSR`/`RET` calls with `GOTO`. Fernflower needs the actual body of the
 *   subroutine to be inlined.
 *
 * - Extends exception handler ranges to cover the `MONITOREXIT` instruction.
 *
 * - Replaces `ASTORE ALOAD MONITORENTER` sequences with `DUP MONITORENTER`,
 *   which prevents Fernflower from emitting a redundant variable declaration.
 *
 * There is one final difference that this transformer does not deal with:
 * modern versions of the Java compiler add a second exception handler range to
 * each synchronized block covering the `MONITOREXIT` sequence, with the
 * handler pointing to the same `MONITOREXIT` sequence. Adding this isn't
 * necessary for Fernflower compatibility.
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
            subroutines.put(match.getFirst(), match);
        }

        for (List<AbstractInsnNode> match : this.JSR_MATCHER.match(method.instructions)) {
            JumpInsnNode jsr = (JumpInsnNode) match.get(1);
            List<AbstractInsnNode> subroutine = subroutines.get(InsnNodeUtil.getNextReal(jsr.label));
            if (subroutine == null) {
                continue;
            }

            JumpInsnNode ret = (JumpInsnNode) subroutine.get(3);
            if (InsnNodeUtil.getNextReal(ret.label) != InsnNodeUtil.getNextReal(jsr)) {
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

            AbstractInsnNode monitorenter = InsnNodeUtil.getPreviousReal(tryCatch.start);
            if (monitorenter.getOpcode() != Opcodes.MONITORENTER) {
                continue;
            }

            AbstractInsnNode aload = InsnNodeUtil.getNextReal(tryCatch.end);
            if (aload.getOpcode() != Opcodes.ALOAD) {
                continue;
            }

            AbstractInsnNode monitorexit = InsnNodeUtil.getNextReal(aload);
            if (monitorexit.getOpcode() != Opcodes.MONITOREXIT) {
                continue;
            }

            AbstractInsnNode end = InsnNodeUtil.getNextReal(monitorexit.getNext());
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
