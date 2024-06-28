package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.runewiki.asm.InsnNodeUtil;
import org.runewiki.asm.transform.Transformer;

import java.util.List;

public class FernflowerExceptionTransformer extends Transformer {
    private int nopsInserted = 0;

    @Override
    public void preTransform(List<ClassNode> classes) {
        nopsInserted = 0;
    }

    @Override
    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        for (var tryCatch : method.tryCatchBlocks) {
            if (InsnNodeUtil.getNextReal(tryCatch.end) == null) {
                method.instructions.add(new InsnNode(Opcodes.NOP));
                nopsInserted++;
            }
        }

        return false;
    }

    @Override
    public void postTransform(List<ClassNode> classes) {
        System.out.println("Inserted " + nopsInserted + " NOPs to correct Fernflower's exception generation");
    }
}
