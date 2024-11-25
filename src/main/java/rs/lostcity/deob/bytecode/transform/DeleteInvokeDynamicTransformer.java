package rs.lostcity.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;
import rs.lostcity.asm.transform.Transformer;

import java.util.List;

public class DeleteInvokeDynamicTransformer extends Transformer {
    @Override
    public boolean transformMethod(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        if ((method.access & Opcodes.ACC_ABSTRACT) != 0) {
            return false;
        }

        var instruction = method.instructions.get(0);

        while (instruction != null) {
            if (instruction instanceof InvokeDynamicInsnNode invokeDynamic) {
                var methodType = Type.getMethodType(invokeDynamic.desc);
                var argumentTypes = methodType.getArgumentTypes();

                for (var i = argumentTypes.length - 1; i >= 0; i--) {
                    var argumentType = argumentTypes[i];

                    if (argumentType.getSize() == 2) {
                        method.instructions.insertBefore(instruction, new InsnNode(Opcodes.POP2));
                    } else {
                        method.instructions.insertBefore(instruction, new InsnNode(Opcodes.POP));
                    }
                }

                if (methodType.getReturnType() == Type.INT_TYPE) {
                    method.instructions.insertBefore(instruction, new InsnNode(Opcodes.ICONST_0));
                } else if (methodType.getReturnType() == Type.LONG_TYPE) {
                    method.instructions.insertBefore(instruction, new InsnNode(Opcodes.LCONST_0));
                } else if (methodType.getReturnType() == Type.FLOAT_TYPE) {
                    method.instructions.insertBefore(instruction, new InsnNode(Opcodes.FCONST_0));
                } else if (methodType.getReturnType() == Type.FLOAT_TYPE) {
                    method.instructions.insertBefore(instruction, new InsnNode(Opcodes.FCONST_0));
                } else if (methodType.getReturnType() != Type.VOID_TYPE) {
                    method.instructions.insertBefore(instruction, new InsnNode(Opcodes.ACONST_NULL));
                }

                instruction = instruction.getNext();
                method.instructions.remove(invokeDynamic);
                continue;
            }

            instruction = instruction.getNext();
        }

        return false;
    }
}
