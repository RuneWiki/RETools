package zwyz.deob;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

public class AnnotationRemover {
    public static void run(List<ClassNode> classes) {
        for (var clazz : classes) {
            removeRuneliteAnnotations(clazz.invisibleAnnotations);
            removeRuneliteAnnotations(clazz.visibleAnnotations);

            for (var method : clazz.methods) {
                removeRuneliteAnnotations(method.invisibleAnnotations);
                removeRuneliteAnnotations(method.visibleAnnotations);
            }

            for (var field : clazz.fields) {
                removeRuneliteAnnotations(field.invisibleAnnotations);
                removeRuneliteAnnotations(field.visibleAnnotations);
            }
        }
    }

    private static void removeRuneliteAnnotations(List<AnnotationNode> annotations) {
        if (annotations != null) {
            annotations.removeIf(a -> true);
        }
    }
}
