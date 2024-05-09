package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.runewiki.asm.transform.Transformer;

import java.util.List;

public class RedundantExceptionTransformer extends Transformer {
    @Override
    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        method.tryCatchBlocks.removeIf(tryCatchBlock -> {
            if (tryCatchBlock.type == null) {
                return false;
            }

            return tryCatchBlock.type.equals("java/lang/RuntimeException") &&
                method.instructions.get(method.instructions.size() - 1).getOpcode() == org.objectweb.asm.Opcodes.ATHROW;
        });

        return false;
    }
}
