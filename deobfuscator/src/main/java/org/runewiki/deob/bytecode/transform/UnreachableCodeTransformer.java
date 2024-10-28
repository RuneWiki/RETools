package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.deob.AsmUtil;

import java.util.List;

public class UnreachableCodeTransformer extends Transformer {
    @Override
    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        AsmUtil.removeUnreachableCode(method);

        return false;
    }
}
