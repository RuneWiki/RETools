package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.runewiki.asm.transform.Transformer;

import java.util.List;

public class AnnotationRemoverTransformer extends Transformer {
    @Override
    public boolean transformClass(List<ClassNode> classes, ClassNode clazz) {
        removeAnnotations(clazz.invisibleAnnotations);
        removeAnnotations(clazz.visibleAnnotations);
        return false;
    }

    @Override
    public boolean transformMethod(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        removeAnnotations(method.invisibleAnnotations);
        removeAnnotations(method.visibleAnnotations);
        return false;
    }

    @Override
    public boolean transformField(List<ClassNode> classes, ClassNode clazz, FieldNode field) {
        removeAnnotations(field.invisibleAnnotations);
        removeAnnotations(field.visibleAnnotations);
        return false;
    }

    private static void removeAnnotations(List<AnnotationNode> annotations) {
        if (annotations != null) {
            annotations.removeIf(a -> true);
        }
    }
}
