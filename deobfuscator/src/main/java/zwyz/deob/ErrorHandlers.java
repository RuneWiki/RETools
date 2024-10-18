package zwyz.deob;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public class ErrorHandlers {
    public static void run(List<ClassNode> classes, LinkedHashSet<String> calledMethods, HashSet<String> obfuscatedMethods, HashSet<String> unobfuscatedMethods) {
        for (var clazz : classes) {
            for (var method : clazz.methods) {
                if (removeErrorHandler(clazz, method)) {
                    obfuscatedMethods.add(method.name);

                    // if (!calledMethods.contains(method.name)) {
                    //     System.out.println("not called " + clazz.name + "." + method.name + method.desc);
                    // }
                } else if ((method.access & Opcodes.ACC_ABSTRACT) == 0) {
                    unobfuscatedMethods.add(method.name);
                }
            }
        }
    }

    private static boolean removeErrorHandler(ClassNode clazz, MethodNode method) {
        return method.tryCatchBlocks.removeIf(tryCatch -> isErrorHandler(clazz, method, tryCatch));
    }

    private static boolean isErrorHandler(ClassNode clazz, MethodNode method, TryCatchBlockNode tryCatch) {
        if (!Objects.equals(tryCatch.type, "java/lang/RuntimeException")) {
            return false;
        }

        // new java/lang/StringBuilder
        var instruction = tryCatch.handler.getNext();
        if (!AsmUtil.isNew(instruction, "java/lang/StringBuilder")) return false;

        // dup
        instruction = instruction.getNext();
        if (!(instruction.getOpcode() == Opcodes.DUP)) return false;

        // invokespecial java/lang/StringBuilder.<init>()V
        instruction = instruction.getNext();
        if (!AsmUtil.isInvokeSpecial(instruction, "java/lang/StringBuilder", "<init>", "()V")) return false;

        // ldc "[class name].[method name]("
        instruction = instruction.getNext();
        if (!(instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.endsWith("("))) return false;

        return true;
    }
}
