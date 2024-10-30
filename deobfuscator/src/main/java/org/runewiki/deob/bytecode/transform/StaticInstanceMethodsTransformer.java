package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.deob.bytecode.AsmUtil;

import java.util.*;

public class StaticInstanceMethodsTransformer extends Transformer {
    @Override
    public void transform(List<ClassNode> classes) {
        var changedMethods = new HashSet<String>();
        var newDescs = new HashMap<String, String>();
        var realOwners = new HashMap<String, String>();

        // Convert static to instance
        for (var clazz : classes) {
            for (var method : clazz.methods) {
                if ((method.access & Opcodes.ACC_STATIC) != 0 && removeNullCheckCall(method)) {
                    method.access &= ~Opcodes.ACC_STATIC;

                    var methodType = Type.getMethodType(method.desc);
                    var argumentTypes = methodType.getArgumentTypes();
                    var realOwner = argumentTypes[0].getClassName();
                    argumentTypes = Arrays.copyOfRange(argumentTypes, 1, argumentTypes.length);
                    methodType = Type.getMethodType(methodType.getReturnType(), argumentTypes);
                    var newDesc = methodType.getDescriptor();
                    newDescs.put(method.name, newDesc);
                    method.desc = newDesc;

                    realOwners.put(method.name, realOwner);
                    changedMethods.add(method.name);
                }
            }
        }

        // Move to new owners
        var classesByName = new HashMap<String, ClassNode>();

        for (var clazz : classes) {
            classesByName.put(clazz.name, clazz);
        }

        for (var clazz : classes) {
            for (var method : new ArrayList<>(clazz.methods)) {
                var realOwner = realOwners.get(method.name);

                if (realOwner != null && !Objects.equals(realOwner, clazz.name)) {
                    clazz.methods.remove(method);
                    classesByName.get(realOwner).methods.add(method);
                }
            }
        }

        // Update references
        for (var clazz : classes) {
            for (var method : clazz.methods) {
                for (var insn : method.instructions) {
                    if (insn instanceof MethodInsnNode methodInsn && methodInsn.getOpcode() == Opcodes.INVOKESTATIC && changedMethods.contains(methodInsn.name)) {
                        methodInsn.setOpcode(Opcodes.INVOKEVIRTUAL);
                        methodInsn.desc = newDescs.get(methodInsn.name);
                        methodInsn.owner = realOwners.get(methodInsn.name);
                    }
                }
            }
        }
    }

    private static boolean removeNullCheckCall(MethodNode method) {
        var argTypes = Type.getMethodType(method.desc).getArgumentTypes();

        if (argTypes.length == 0) {
            return false; // no args
        }

        var instruction = method.instructions.getFirst();

        // aload 0
        if (!AsmUtil.isAload(instruction, 0)) {
            return false;
        }

        // ifnonnull
        instruction = instruction.getNext();

        if (instruction.getOpcode() != Opcodes.IFNONNULL) {
            return false;
        }

        var label = ((JumpInsnNode) instruction).label;

        // any number of loads
        do {
            instruction = instruction.getNext();
        } while (AsmUtil.isLoad(instruction));

        // invokevirtual
        if (instruction.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return false;
        }

        // skip from start to label
        method.instructions.insertBefore(method.instructions.getFirst(), new JumpInsnNode(Opcodes.GOTO, label));

        // remove dead code now so that parameter checks pass doesn't see the reference
        AsmUtil.removeUnreachableCode(method);
        return true;
    }
}
