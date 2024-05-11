package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.transform.Transformer;

import java.util.List;

/*
 * Default undeclared class, field, and method visibility to public
 */
public class VisibilityTransformer extends Transformer {
    @Override
    public boolean preTransformClass(List<ClassNode> classes, ClassNode clazz) {
        clazz.access = (clazz.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
        clazz.access = clazz.access & ~Opcodes.ACC_FINAL;

        clazz.fields.forEach(field -> {
            if ((field.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE)) == 0) {
                field.access = (field.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
            }
        });

        clazz.methods.forEach(method -> {
            if ((method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE)) == 0) {
                method.access = (method.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
            }
        });

        return false;
    }
}
