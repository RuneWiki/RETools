package org.runewiki.deob;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class AsmUtil {
    private static final Pattern OBF_PATTERN = Pattern.compile("[A-Za-z]{1,3}");
    private static final Set<String> OBF_EXCLUDED = Set.of("run", "add", "put", "get", "set", "uid", "dns");

    public static boolean isAload(AbstractInsnNode instruction, int var) {
        return instruction.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) instruction).var == var;
    }

    public static boolean isIload(AbstractInsnNode instruction, int var) {
        return instruction.getOpcode() == Opcodes.ILOAD && ((VarInsnNode) instruction).var == var;
    }

    public static boolean isLoad(AbstractInsnNode instruction) {
        if (instruction.getOpcode() == Opcodes.ALOAD) return true;
        if (instruction.getOpcode() == Opcodes.ILOAD) return true;
        if (instruction.getOpcode() == Opcodes.LLOAD) return true;
        if (instruction.getOpcode() == Opcodes.FLOAD) return true;
        if (instruction.getOpcode() == Opcodes.DLOAD) return true;
        return false;
    }

    public static boolean isIntComparison(AbstractInsnNode instruction) {
        if (instruction.getOpcode() == Opcodes.IF_ICMPEQ) return true;
        if (instruction.getOpcode() == Opcodes.IF_ICMPGE) return true;
        if (instruction.getOpcode() == Opcodes.IF_ICMPGT) return true;
        if (instruction.getOpcode() == Opcodes.IF_ICMPLE) return true;
        if (instruction.getOpcode() == Opcodes.IF_ICMPLT) return true;
        if (instruction.getOpcode() == Opcodes.IF_ICMPNE) return true;
        return false;
    }

    public static boolean isIntConstant(AbstractInsnNode instruction) {
        return getIntConstant(instruction) != null;
    }

    public static Integer getIntConstant(AbstractInsnNode instruction) {
        if (instruction.getOpcode() == Opcodes.ICONST_M1) return -1;
        if (instruction.getOpcode() == Opcodes.ICONST_0) return 0;
        if (instruction.getOpcode() == Opcodes.ICONST_1) return 1;
        if (instruction.getOpcode() == Opcodes.ICONST_2) return 2;
        if (instruction.getOpcode() == Opcodes.ICONST_3) return 3;
        if (instruction.getOpcode() == Opcodes.ICONST_4) return 4;
        if (instruction.getOpcode() == Opcodes.ICONST_5) return 5;
        if (instruction.getOpcode() == Opcodes.BIPUSH) return ((IntInsnNode) instruction).operand;
        if (instruction.getOpcode() == Opcodes.SIPUSH) return ((IntInsnNode) instruction).operand;
        if (instruction.getOpcode() == Opcodes.LDC && ((LdcInsnNode) instruction).cst instanceof Integer) return ((Integer) ((LdcInsnNode) instruction).cst);
        return null;
    }

    public static boolean isInvokeSpecial(AbstractInsnNode instruction, String owner, String name, String desc) {
        return instruction.getOpcode() == Opcodes.INVOKESPECIAL &&
                Objects.equals(((MethodInsnNode) instruction).owner, owner) &&
                Objects.equals(((MethodInsnNode) instruction).name, name) &&
                Objects.equals(((MethodInsnNode) instruction).desc, desc);
    }

    public static boolean isNew(AbstractInsnNode instruction, String type) {
        return instruction.getOpcode() == Opcodes.NEW && Objects.equals(((TypeInsnNode) instruction).desc, type);
    }

    public static boolean isLdc(AbstractInsnNode instruction, String cst) {
        return instruction.getOpcode() == Opcodes.LDC && Objects.equals(((LdcInsnNode) instruction).cst, cst);
    }

    public static void replaceRange(InsnList list, AbstractInsnNode start, AbstractInsnNode end, JumpInsnNode instruction) {
        while (start.getNext() != end) {
            list.remove(start.getNext());
        }

        list.insert(start, instruction);
        list.remove(start);
        list.remove(end);
    }

    public static void removeUnreachableCode(MethodNode method) {
        var queue = new ArrayDeque<AbstractInsnNode>();
        var visited = new LinkedHashSet<AbstractInsnNode>();
        queue.add(method.instructions.getFirst());

        for (var tryCatch : method.tryCatchBlocks) {
            queue.add(tryCatch.handler);
        }

        while (!queue.isEmpty()) {
            var instruction = queue.removeFirst();

            if (visited.add(instruction)) {
                if (!isTerminal(instruction)) {
                    queue.add(instruction.getNext());
                }

                if (instruction instanceof TableSwitchInsnNode tableSwitch) {
                    queue.addAll(tableSwitch.labels);
                    queue.add(tableSwitch.dflt);
                }

                if (instruction instanceof LookupSwitchInsnNode lookupSwitch) {
                    queue.addAll(lookupSwitch.labels);
                    queue.add(lookupSwitch.dflt);
                }

                if (instruction instanceof JumpInsnNode jump) {
                    queue.add(jump.label);
                }
            }
        }

        var current = method.instructions.getFirst();

        while (current != null) {
            if (current.getNext() != null && !visited.contains(current.getNext())) {
                method.instructions.remove(current.getNext());
            } else {
                current = current.getNext();
            }
        }
    }

    public static boolean isTerminal(AbstractInsnNode instruction) {
        return instruction.getOpcode() == Opcodes.GOTO ||
                instruction.getOpcode() == Opcodes.RETURN ||
                instruction.getOpcode() == Opcodes.ARETURN ||
                instruction.getOpcode() == Opcodes.IRETURN ||
                instruction.getOpcode() == Opcodes.LRETURN ||
                instruction.getOpcode() == Opcodes.FRETURN ||
                instruction.getOpcode() == Opcodes.DRETURN ||
                instruction.getOpcode() == Opcodes.ATHROW ||
                instruction.getOpcode() == Opcodes.TABLESWITCH ||
                instruction.getOpcode() == Opcodes.LOOKUPSWITCH;
    }

    public static void removeGotoNext(MethodNode method) {
        var instruction = method.instructions.getFirst();

        while (instruction != null) {
            var next = instruction.getNext();

            if (instruction instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.GOTO && jump.label == instruction.getNext()) {
                method.instructions.remove(instruction);
            }

            instruction = next;
        }
    }

    public static void removeUnusedLabels(MethodNode method) {
        var usedLabels = new HashSet<LabelNode>();

        for (var tryCatch : method.tryCatchBlocks) {
            usedLabels.add(tryCatch.start);
            usedLabels.add(tryCatch.end);
            usedLabels.add(tryCatch.handler);
        }

        for (var instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode jump) {
                usedLabels.add(jump.label);
            }

            if (instruction instanceof TableSwitchInsnNode tableSwitch) {
                usedLabels.add(tableSwitch.dflt);
                usedLabels.addAll(tableSwitch.labels);
            }

            if (instruction instanceof LookupSwitchInsnNode lookupSwitch) {
                usedLabels.add(lookupSwitch.dflt);
                usedLabels.addAll(lookupSwitch.labels);
            }
        }

        removeIf(method.instructions, instruction -> instruction instanceof LabelNode label && !usedLabels.contains(label));
    }

    public static void removeIf(InsnList instructions, Predicate<AbstractInsnNode> condition) {
        var instruction = instructions.getFirst();

        while (instruction != null) {
            var next = instruction.getNext();

            if (condition.test(instruction)) {
                instructions.remove(instruction);
            }

            instruction = next;
        }
    }

    public static List<LabelNode> getJumpTargets(AbstractInsnNode instruction) {
        if (instruction instanceof JumpInsnNode jump) {
            return List.of(jump.label);
        }

        if (instruction instanceof TableSwitchInsnNode tableSwitch) {
            var result = new ArrayList<LabelNode>(tableSwitch.labels.size() + 1);
            result.addAll(tableSwitch.labels);
            result.add(tableSwitch.dflt);
            return result;
        }

        if (instruction instanceof LookupSwitchInsnNode lookupSwitch) {
            var result = new ArrayList<LabelNode>(lookupSwitch.labels.size() + 1);
            result.addAll(lookupSwitch.labels);
            result.add(lookupSwitch.dflt);
            return result;
        }

        return List.of();
    }

    public static int getArgumentVariableIndex(MethodNode method, int index) {
        var isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        var argumentTypes = Type.getMethodType(method.desc).getArgumentTypes();

        var argumentVariableIndex = isStatic ? 0 : 1;

        for (var i = 0; i < index; i++) {
            argumentVariableIndex += argumentTypes[i].getSize();
        }

        return argumentVariableIndex;
    }

    public static int getFirstLocalIndex(MethodNode method) {
        var isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        var argumentTypes = Type.getMethodType(method.desc).getArgumentTypes();
        var argumentVariableIndex = isStatic ? 0 : 1;

        for (var argumentType : argumentTypes) {
            argumentVariableIndex += argumentType.getSize();
        }

        return argumentVariableIndex;
    }

    public static int getLoadedVar(AbstractInsnNode instruction) {
        if (instruction.getOpcode() == Opcodes.ILOAD || instruction.getOpcode() == Opcodes.LLOAD || instruction.getOpcode() == Opcodes.FLOAD || instruction.getOpcode() == Opcodes.DLOAD || instruction.getOpcode() == Opcodes.ALOAD) {
            return ((VarInsnNode) instruction).var;
        }

        if (instruction.getOpcode() == Opcodes.IINC) {
            return ((IincInsnNode) instruction).var;
        }

        return -1;
    }

    public static int getStoredVar(AbstractInsnNode instruction) {
        if (instruction.getOpcode() == Opcodes.ISTORE || instruction.getOpcode() == Opcodes.LSTORE || instruction.getOpcode() == Opcodes.FSTORE || instruction.getOpcode() == Opcodes.DSTORE || instruction.getOpcode() == Opcodes.ASTORE) {
            return ((VarInsnNode) instruction).var;
        }

        if (instruction.getOpcode() == Opcodes.IINC) {
            return ((IincInsnNode) instruction).var;
        }

        return -1;
    }

    public static boolean isClassObfuscated(String name) {
        if (name.equals(name.toUpperCase()) && name.length() == 8) {
            // 2005-2006
            return true;
        }
        return OBF_PATTERN.matcher(name).matches();
    }

    public static boolean isFieldObfuscated(String name) {
        return OBF_PATTERN.matcher(name).matches() && !OBF_EXCLUDED.contains(name);
    }

    public static boolean isMethodObfuscated(String name) {
        return OBF_PATTERN.matcher(name).matches() && !OBF_EXCLUDED.contains(name);
    }

    public static boolean isComparison(int opcode) {
        return opcode == Opcodes.LCMP || opcode == Opcodes.FCMPL || opcode == Opcodes.FCMPG || opcode == Opcodes.DCMPL || opcode == Opcodes.DCMPG;
    }
}
