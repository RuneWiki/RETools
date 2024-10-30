package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.runewiki.asm.InsnMatcher;
import org.runewiki.asm.InsnNodeUtil;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.deob.bytecode.AsmUtil;

import java.util.List;

public class BitShiftTransformer extends Transformer {
    private final InsnMatcher CONST_SHIFT_MATCHER = InsnMatcher.compile(
        "(ICONST | BIPUSH | SIPUSH | LDC) (ISHL | ISHR | IUSHR | LSHL | LSHR | LUSHR)"
    );
    private final List<Integer> LONG_SHIFTS = List.of(Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR);

    private int simplified;

    @Override
    public void preTransform(List<ClassNode> classes) {
        this.simplified = 0;
    }

    @Override
    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        for (var match : CONST_SHIFT_MATCHER.match(method)) {
            var push = match.getFirst();
            if (!AsmUtil.isIntConstant(push)) {
                continue;
            }

            int bits = AsmUtil.getIntConstant(push);

            int opcode = match.get(1).getOpcode();
            int mask = LONG_SHIFTS.contains(opcode) ? 63 : 31;

            int simplifiedBits = bits & mask;

            if (bits != simplifiedBits) {
                method.instructions.set(push, InsnNodeUtil.toAbstractInsnNode(simplifiedBits));
                simplified++;
            }
        }

        return false;
    }

    @Override
    public void postTransform(List<ClassNode> classes) {
        System.out.println("Simplified " + this.simplified + " bit shifts");
    }
}
