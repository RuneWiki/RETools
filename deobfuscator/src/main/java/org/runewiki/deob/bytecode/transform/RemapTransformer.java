package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.transform.Transformer;
import zwyz.deob.UniqueRenamer;

import java.util.List;

public class RemapTransformer extends Transformer {
    @Override
    public void preTransform(List<ClassNode> classes) {
        UniqueRenamer.remap(classes);
    }
}
