package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.runewiki.asm.transform.Transformer;
import zwyz.deob.AsmUtil;
import zwyz.deob.GotoDeobfuscator;

import java.util.List;

/*
 * Remove redundant GOTO instructions
 */
public class RedundantGotoTransformer extends Transformer {
    @Override
    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        if (method.tryCatchBlocks.isEmpty()) {
            GotoDeobfuscator.sortBlocks(method);
        }

        AsmUtil.removeGotoNext(method);
        AsmUtil.removeUnusedLabels(method);
        return false;
    }
}
