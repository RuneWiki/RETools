package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.runewiki.asm.transform.Transformer;
import zwyz.deob.VariableSplitter;

import java.util.List;

public class VariableSplitterTransformer extends Transformer {
    @Override
    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        if ((method.access & Opcodes.ACC_ABSTRACT) != 0) {
            return false;
        }

        new VariableSplitter().run(method);
        return false;
    }
}
