package zwyz.deob;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class AnnotateObfuscatedNames {
    public static void annotate(List<ClassNode> classes) {
        var obfuscatedNameClass = new ClassNode();
        obfuscatedNameClass.version = Opcodes.V1_6;
        obfuscatedNameClass.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ANNOTATION;
        obfuscatedNameClass.name = "ObfuscatedName";
        obfuscatedNameClass.superName = "java/lang/Object";
        obfuscatedNameClass.sourceFile = "ObfuscatedName.java";
        var obfuscatedNameValueMethod = new MethodNode();
        obfuscatedNameValueMethod.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        obfuscatedNameValueMethod.name = "value";
        obfuscatedNameValueMethod.desc = "()Ljava/lang/String;";
        obfuscatedNameClass.methods.add(obfuscatedNameValueMethod);
        classes.add(obfuscatedNameClass);

        for (var clazz : classes) {
            if (ZwyzDeobStep1.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
                continue;
            }

            if (clazz != obfuscatedNameClass && AsmUtil.isClassObfuscated(clazz.name.substring(clazz.name.lastIndexOf('/') + 1))) {
                var classAnnotation = clazz.visitAnnotation("L" + obfuscatedNameClass.name + ";", false);
                classAnnotation.visit("value", clazz.name);
                classAnnotation.visitEnd();
            }

            for (var field : clazz.fields) {
                if (AsmUtil.isFieldObfuscated(field.name)) {
                    var fieldAnnotation = field.visitAnnotation("L" + obfuscatedNameClass.name + ";", false);
                    fieldAnnotation.visit("value", clazz.name + "." + field.name);
                    fieldAnnotation.visitEnd();
                }
            }

            for (var method : clazz.methods) {
                if (AsmUtil.isMethodObfuscated(method.name)) {
                    var methodAnnotation = method.visitAnnotation("L" + obfuscatedNameClass.name + ";", false);
                    methodAnnotation.visit("value", clazz.name + "." + method.name + method.desc);
                    methodAnnotation.visitEnd();
                }
            }
        }
    }
}
