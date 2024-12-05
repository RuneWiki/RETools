package rs.lostcity.deob.bytecode.transform.zwyz;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import rs.lostcity.asm.transform.Transformer;
import rs.lostcity.deob.bytecode.AsmUtil;

import java.util.List;

public class AnnotateObfuscatedNamesTransformer extends Transformer {
    private ClassNode obfuscatedNameClass = null;
    private MethodNode obfuscatedNameValueMethod = null;

    @Override
    public void preTransform(List<ClassNode> classes) {
        obfuscatedNameClass = new ClassNode();
        obfuscatedNameClass.version = Opcodes.V1_6;
        obfuscatedNameClass.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ANNOTATION;
        obfuscatedNameClass.name = "ObfuscatedName";
        obfuscatedNameClass.superName = "java/lang/Object";
        obfuscatedNameClass.sourceFile = "ObfuscatedName.java";

        obfuscatedNameValueMethod = new MethodNode();
        obfuscatedNameValueMethod.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        obfuscatedNameValueMethod.name = "value";
        obfuscatedNameValueMethod.desc = "()Ljava/lang/String;";
        obfuscatedNameClass.methods.add(obfuscatedNameValueMethod);

        classes.add(obfuscatedNameClass);
    }

    @Override
    public boolean transformClass(List<ClassNode> classes, ClassNode clazz) {
        if (ZwyzLegacyLogic.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
            return false;
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

        return false;
    }
}
