package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.runewiki.asm.AsmUtil;
import org.runewiki.asm.transform.Transformer;

import java.util.List;
import java.util.Objects;

/*
 * Remove redundant GOTO instructions
 */
public class RedundantGotoTransformer extends Transformer {
    private int removed = 0;

    @Override
    public void preTransform(List<ClassNode> classes) {
        this.removed = 0;
    }

    @Override
    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        for (AbstractInsnNode insn : method.instructions) {
            if (
                insn.getOpcode() == Opcodes.GOTO &&
                AsmUtil.getNextReal(insn) == AsmUtil.getNextReal(((JumpInsnNode) insn).label)
            ) {
                method.instructions.remove(insn);
                this.removed++;
            }
        }

        return false;
    }

    @Override
    public void postTransform(List<ClassNode> classes) {
        System.out.println("Removed " + this.removed + " redundant GOTO instructions");
    }
}
