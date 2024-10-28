package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.deob.AsmUtil;
import org.tomlj.TomlParseResult;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class ParameterChecksTransformer extends Transformer {
    private static boolean RUNELITE = false;
    private static boolean COMPLEX_PARAMETER_CHECKS = false;

    @Override
    public void provide(TomlParseResult profile) {
        super.provide(profile);

        RUNELITE = Boolean.TRUE.equals(profile.getBoolean("profile.parameter_checks.runelite"));
        COMPLEX_PARAMETER_CHECKS = Boolean.TRUE.equals(profile.getBoolean("profile.parameter_checks.complex"));
    }

    @Override
    public void transform(List<ClassNode> classes) {
        // scan for constants
        var exclude = new HashSet<String>();

        if (COMPLEX_PARAMETER_CHECKS) {
            for (var clazz : classes) {
                for (var method : clazz.methods) {
                    var instruction = method.instructions.getFirst();

                    while (instruction != null) {
                        if (instruction instanceof MethodInsnNode mi) {
                            var type = Type.getMethodType(mi.desc);
                            var argumentTypes = type.getArgumentTypes();

                            if (argumentTypes.length == 0) {
                                exclude.add(mi.name);
                            } else {
                                if (AsmUtil.isIntConstant(instruction.getPrevious()) || RUNELITE && instruction.getPrevious() instanceof VarInsnNode) {
                                    // could remove
                                } else {
                                    exclude.add(mi.name);
                                }
                            }
                        }

                        instruction = instruction.getNext();
                    }
                }
            }
        }

        // Remove parameter checks. The obfuscator only adds check parameters to methods if
        //  - all linked methods are either abstract or obfuscated
        //  - the method isn't linked to a library method, which can't be modified
        //  - the last parameter is not a long or a double
        //  - the method isn't the error handler method
        var parameterCheckRemoved = new HashSet<String>();

        for (var clazz : classes) {
            for (var method : clazz.methods) {
                var allLinkedMethodsObfuscated = ZwyzLegacyLogic.obfuscatedMethods.contains(method.name) && !ZwyzLegacyLogic.unobfuscatedMethods.contains(method.name);
                var linkedToLibraryMethod = !method.name.startsWith("method");
                var argTypes = Type.getMethodType(method.desc).getArgumentTypes();
                var lastParameterLongOrDouble = argTypes.length > 0 && (Objects.equals(argTypes[argTypes.length - 1], Type.DOUBLE_TYPE) || Objects.equals(argTypes[argTypes.length - 1], Type.LONG_TYPE));
                var insideIncludedLibrary = clazz.name.contains("/"); // rs3
                var shouldRemove = allLinkedMethodsObfuscated && !linkedToLibraryMethod && !lastParameterLongOrDouble && !insideIncludedLibrary;

                if (shouldRemove) {
                    if (exclude.contains(method.name) || !removeParameterCheck(clazz, method)) {
                        // System.out.println("not checked " + clazz.name + "." + method.name + method.desc);
                    } else {
                        parameterCheckRemoved.add(method.name);
                    }
                }
            }
        }

        for (var clazz : classes) {
            for (var method : clazz.methods) {
                var instruction = method.instructions.getFirst();

                while (instruction != null) {
                    if (instruction instanceof MethodInsnNode mi && parameterCheckRemoved.contains(mi.name)) {
                        var type = Type.getMethodType(mi.desc);
                        var argumentTypes = type.getArgumentTypes();
                        mi.desc = Type.getMethodType(type.getReturnType(), Arrays.copyOfRange(argumentTypes, 0, argumentTypes.length - 1)).getDescriptor();

                        if (AsmUtil.isIntConstant(instruction.getPrevious()) || RUNELITE && instruction.getPrevious() instanceof VarInsnNode) {
                            method.instructions.remove(instruction.getPrevious());
                        } else {
                            throw new AssertionError();
                        }
                    }

                    instruction = instruction.getNext();
                }
            }
        }
    }

    private static boolean removeParameterCheck(ClassNode clazz, MethodNode method) {
        var type = Type.getMethodType(method.desc);
        var lastArgumentIndex = type.getArgumentTypes().length - 1;

        if (lastArgumentIndex == -1) {
            return false;
        }

        var lastArgumentType = type.getArgumentTypes()[lastArgumentIndex];

        if (!Objects.equals(lastArgumentType, Type.BYTE_TYPE) && !Objects.equals(lastArgumentType, Type.SHORT_TYPE) && !Objects.equals(lastArgumentType, Type.INT_TYPE)) {
            return false;
        }

        var lastArgumentVariableIndex = AsmUtil.getArgumentVariableIndex(method, lastArgumentIndex);

        var kept = false;
        var removed = false;

        var original = new MethodNode();
        method.instructions.accept(original);

        var instruction = method.instructions.getFirst();

        while (instruction != null) {
            if (removeParameterCheck(clazz, method, instruction, lastArgumentVariableIndex)) {
                removed = true;
            } else if (instruction instanceof VarInsnNode varInsn && varInsn.var == lastArgumentVariableIndex) {
                kept = true;
            }

            instruction = instruction.getNext();
        }

        if (removed && kept) {
            if (COMPLEX_PARAMETER_CHECKS) {
                method.instructions = original.instructions;
                return false; // revert
            } else {
                throw new IllegalStateException("removed a non-check parameter");
            }
        }

        if (!removed && kept) {
            return false;
        }

        // Remove the parameter
        type = Type.getMethodType(type.getReturnType(), Arrays.copyOfRange(type.getArgumentTypes(), 0, type.getArgumentTypes().length - 1));
        method.desc = type.getDescriptor();

        // Update local indices so that matching methods works properly
        method.maxLocals--;

        for (var insn : method.instructions) {
            if (insn instanceof VarInsnNode varInsn && varInsn.var > lastArgumentVariableIndex) {
                varInsn.var--;
            }

            if (insn instanceof IincInsnNode iincInsn && iincInsn.var > lastArgumentVariableIndex) {
                iincInsn.var--;
            }
        }

        return true;
    }

    private static boolean removeParameterCheck(ClassNode clazz, MethodNode method, AbstractInsnNode instruction, int lastArgumentIndex) {
        var first = instruction;

        // int load
        if (!AsmUtil.isIload(instruction, lastArgumentIndex)) return false;

        // int constant
        instruction = instruction.getNext();
        if (!AsmUtil.isIntConstant(instruction)) {
            return false;
        }

        // int comparison
        instruction = instruction.getNext();
        if (!AsmUtil.isIntComparison(instruction)) {
            return false;
        }

        var endLabel = ((JumpInsnNode) instruction).label;

        if (!COMPLEX_PARAMETER_CHECKS) {
            instruction = instruction.getNext();
            if (AsmUtil.isNew(instruction, "java/lang/IllegalStateException")) { // new java/lang/IllegalStateException
                // dup
                instruction = instruction.getNext();
                if (!(instruction.getOpcode() == Opcodes.DUP)) {
                    return false;
                }

                // invokespecial java/lang/IllegalStateException.<init>()V
                instruction = instruction.getNext();
                if (!AsmUtil.isInvokeSpecial(instruction, "java/lang/IllegalStateException", "<init>", "()V")) {
                    return false;
                }

                // athrow
                instruction = instruction.getNext();
                if (!(instruction.getOpcode() == Opcodes.ATHROW)) {
                    return false;
                }
            } else if (instruction.getOpcode() == Opcodes.RETURN) { // return
                // ok
            } else {
                if (!RUNELITE) {
                    return false;
                }
            }
        }

        // replace the check with a goto
        method.instructions.insertBefore(first, new JumpInsnNode(Opcodes.GOTO, endLabel));
        return true;
    }
}
