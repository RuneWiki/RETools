package rs.lostcity.deob.bytecode.transform.zwyz;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import rs.lostcity.asm.transform.Transformer;
import rs.lostcity.deob.bytecode.AsmUtil;

import java.util.List;
import java.util.Objects;

public class ErrorHandlersTransformer extends Transformer {
    @Override
    public boolean transformMethod(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        if (removeErrorHandler(clazz, method)) {
            ZwyzLegacyLogic.obfuscatedMethods.add(method.name);
        } else if ((method.access & Opcodes.ACC_ABSTRACT) == 0) {
            ZwyzLegacyLogic.unobfuscatedMethods.add(method.name);
        }

        return false;
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
