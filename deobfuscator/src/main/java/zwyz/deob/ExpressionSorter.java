package zwyz.deob;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class ExpressionSorter {
    public static void run(List<ClassNode> classes) {
        for (var clazz : classes) {
            for (var method : clazz.methods) {
                if ((method.access & Opcodes.ACC_ABSTRACT) == 0) {
                    run(method);
                }
            }
        }
    }

    public static void run(MethodNode method) {
        var newInstructions = new MethodNode();
        var stack = new ArrayDeque<Expression>();

        for (var instruction : method.instructions) {
            var expression = createExpression(instruction, stack);

            if (expression != null) {
                stack.addLast(expression);
            } else {
                if (stack.size() >= 2 && isCommutativeCondition(instruction.getOpcode())) {
                    var b = stack.removeLast();
                    var a = stack.removeLast();

                    if (!shouldSwap(a, b)) {
                        stack.addLast(a);
                        stack.addLast(b);
                    } else {
                        stack.addLast(b);
                        stack.addLast(a);
                    }
                }

                if (stack.size() >= 1 && AsmUtil.isComparison(stack.getLast().opcode()) && (instruction.getOpcode() == Opcodes.IFEQ || instruction.getOpcode() == Opcodes.IFNE)) {
                    var cmp = ((Expression.BinaryExpression) stack.removeLast());
                    var a = cmp.argument1();
                    var b = cmp.argument2();

                    if (!shouldSwap(a, b)) {
                        stack.addLast(new Expression.BinaryExpression(cmp.opcode(), a, b));
                    } else {
                        stack.addLast(new Expression.BinaryExpression(cmp.opcode(), b, a));
                    }
                }

                while (!stack.isEmpty()) {
                    place(newInstructions, stack.removeFirst());
                }

                instruction.accept(newInstructions);
            }
        }

        for (var tryCatchBlock : method.tryCatchBlocks) {
            tryCatchBlock.accept(newInstructions);
        }

        while (!stack.isEmpty()) {
            place(newInstructions, stack.removeFirst());
        }

        method.instructions = newInstructions.instructions;
        method.tryCatchBlocks = newInstructions.tryCatchBlocks;
    }

    private static boolean isCommutativeCondition(int opcode) {
        return opcode == Opcodes.IF_ICMPEQ || opcode == Opcodes.IF_ICMPNE || opcode == Opcodes.IF_ACMPEQ || opcode == Opcodes.IF_ACMPNE;
    }

    private static boolean shouldSwap(Expression argument1, Expression argument2) {
        if (Objects.equals(argument1, argument2)) {
            return false;
        }

        var left1 = leftPriority(argument1.opcode());
        var left2 = leftPriority(argument2.opcode());
        if (left1 > left2) return false;
        if (left1 < left2) return true;

        if (argument1 instanceof Expression.LoadExpression load1 && argument2 instanceof Expression.LoadExpression load2) {
            if (load1.var < load2.var) return false;
            if (load1.var > load2.var) return true;
        }

        if (argument1 instanceof Expression.UnaryExpression unary1 && argument2 instanceof Expression.UnaryExpression unary2) {
            return shouldSwap(unary1.argument(), unary2.argument());
        }

        if (argument1 instanceof Expression.StaticInstruction field1 && argument2 instanceof Expression.StaticInstruction field2) {
            if (!Objects.equals(field1.desc(), field2.desc())) {
                return field1.desc().compareTo(field2.desc()) < 0;
            }

            if (!Objects.equals(field1.owner(), field2.owner())) {
                return field1.owner().compareTo(field2.owner()) < 0;
            }

            if (!Objects.equals(field1.name(), field2.name())) {
                return field1.name().compareTo(field2.name()) < 0;
            }
        }

        if (argument1 instanceof Expression.FieldInstruction field1 && argument2 instanceof Expression.FieldInstruction field2) {
            if (!Objects.equals(field1.argument(), field2.argument())) {
                return shouldSwap(field1.argument(), field2.argument());
            }

            if (!Objects.equals(field1.desc(), field2.desc())) {
                return field1.desc().compareTo(field2.desc()) < 0;
            }

            if (!Objects.equals(field1.owner(), field2.owner())) {
                return field1.owner().compareTo(field2.owner()) < 0;
            }

            if (!Objects.equals(field1.name(), field2.name())) {
                return field1.name().compareTo(field2.name()) < 0;
            }
        }

        if (argument1 instanceof Expression.BinaryExpression binary1 && argument2 instanceof Expression.BinaryExpression binary2) {
            var swap1 = shouldSwap(binary1.argument1(), binary1.argument2());
            var swap2 = shouldSwap(binary2.argument1(), binary2.argument2());
            var arg11 = !swap1 ? binary1.argument1() : binary1.argument2();
            var arg12 = !swap1 ? binary1.argument2() : binary1.argument1();
            var arg21 = !swap2 ? binary2.argument1() : binary2.argument2();
            var arg22 = !swap2 ? binary2.argument2() : binary2.argument1();

            if (!Objects.equals(arg11, arg21)) {
                return shouldSwap(arg11, arg21);
            }

            if (!Objects.equals(arg12, arg22)) {
                return shouldSwap(arg12, arg22);
            }

            return false; // equal after sorting subexpressions
        }

        var constant1 = getConstantValue(argument1);
        var constant2 = getConstantValue(argument1);

        if (constant1 instanceof Integer i1 && constant2 instanceof Integer i2) return i1 > i2;
        if (constant1 instanceof Long i1 && constant2 instanceof Long i2) return i1 > i2;
        if (constant1 instanceof Float i1 && constant2 instanceof Float i2) return i1 > i2;
        if (constant1 instanceof Double i1 && constant2 instanceof Double i2) return i1 > i2;

        return false;
    }

    private static Object getConstantValue(Expression expression) {
        if (expression.opcode() == Opcodes.ICONST_M1) return -1;
        if (expression.opcode() == Opcodes.ICONST_0) return 1;
        if (expression.opcode() == Opcodes.ICONST_1) return 1;
        if (expression.opcode() == Opcodes.ICONST_2) return 1;
        if (expression.opcode() == Opcodes.ICONST_3) return 1;
        if (expression.opcode() == Opcodes.ICONST_4) return 1;
        if (expression.opcode() == Opcodes.ICONST_5) return 1;
        if (expression.opcode() == Opcodes.LCONST_0) return 0L;
        if (expression.opcode() == Opcodes.LCONST_1) return 1L;
        if (expression.opcode() == Opcodes.FCONST_0) return 1F;
        if (expression.opcode() == Opcodes.FCONST_1) return 1F;
        if (expression.opcode() == Opcodes.FCONST_2) return 1F;
        if (expression.opcode() == Opcodes.DCONST_0) return 1D;
        if (expression.opcode() == Opcodes.DCONST_1) return 1D;
        if (expression instanceof Expression.IntExpression ie) return ie.operand();
        if (expression instanceof Expression.LdcExpression ldc) return ldc.constant();
        return null;
    }

    private static int leftPriority(int opcode) {
        return switch (opcode) {
            // binary
            case Opcodes.IUSHR -> 9901;
            case Opcodes.LUSHR -> 9902;
            case Opcodes.ISHL -> 9801;
            case Opcodes.LSHL -> 9802;
            case Opcodes.ISHR -> 9701;
            case Opcodes.LSHR -> 9702;
            case Opcodes.IXOR -> 9601;
            case Opcodes.LXOR -> 9602;
            case Opcodes.IAND -> 9501;
            case Opcodes.LAND -> 9502;
            case Opcodes.IOR -> 9401;
            case Opcodes.LOR -> 9402;
            case Opcodes.IREM -> 9301;
            case Opcodes.LREM -> 9302;
            case Opcodes.FREM -> 9201;
            case Opcodes.DREM -> 9202;
            case Opcodes.IDIV -> 9101;
            case Opcodes.LDIV -> 9102;
            case Opcodes.FDIV -> 9103;
            case Opcodes.DDIV -> 9104;
            case Opcodes.IMUL -> 9001;
            case Opcodes.LMUL -> 9002;
            case Opcodes.FMUL -> 9003;
            case Opcodes.DMUL -> 9004;
            case Opcodes.ISUB -> 8901;
            case Opcodes.LSUB -> 8902;
            case Opcodes.FSUB -> 8903;
            case Opcodes.DSUB -> 8904;
            case Opcodes.IADD -> 8801;
            case Opcodes.LADD -> 8802;
            case Opcodes.FADD -> 8803;
            case Opcodes.DADD -> 8804;
            case Opcodes.IALOAD -> 8701;
            case Opcodes.LALOAD -> 8702;
            case Opcodes.FALOAD -> 8703;
            case Opcodes.DALOAD -> 8704;
            case Opcodes.AALOAD -> 8601;
            case Opcodes.BALOAD -> 8602;
            case Opcodes.CALOAD -> 8603;
            case Opcodes.SALOAD -> 8604;
            case Opcodes.LCMP -> 8501;
            case Opcodes.FCMPL -> 8502;
            case Opcodes.FCMPG -> 8503;
            case Opcodes.DCMPL -> 8504;
            case Opcodes.DCMPG -> 8505;

            // Unary
            case Opcodes.ARRAYLENGTH -> 5601;

            case Opcodes.I2L -> 5401;
            case Opcodes.I2F -> 5402;
            case Opcodes.I2D -> 5403;
            case Opcodes.L2I -> 5404;
            case Opcodes.L2F -> 5405;
            case Opcodes.L2D -> 5406;
            case Opcodes.F2I -> 5407;
            case Opcodes.F2L -> 5408;
            case Opcodes.F2D -> 5409;
            case Opcodes.D2I -> 5410;
            case Opcodes.D2L -> 5411;
            case Opcodes.D2F -> 5412;
            case Opcodes.I2B -> 5413;
            case Opcodes.I2C -> 5414;
            case Opcodes.I2S -> 5415;
            case Opcodes.INEG -> 5200;
            case Opcodes.LNEG -> 5201;
            case Opcodes.FNEG -> 5202;
            case Opcodes.DNEG -> 5203;

            case Opcodes.GETSTATIC -> 1020;
            case Opcodes.GETFIELD -> 1010;

            case Opcodes.ILOAD -> 1001;
            case Opcodes.LLOAD -> 1002;
            case Opcodes.FLOAD -> 1003;
            case Opcodes.DLOAD -> 1004;
            case Opcodes.ALOAD -> 1005;

            // Constants need to be to the right for multiplier deobfuscation, and for sorting subexpressions with multipliers
            case Opcodes.ACONST_NULL, Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2,
                    Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.LCONST_0, Opcodes.LCONST_1,
                    Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2, Opcodes.DCONST_0, Opcodes.DCONST_1,
                    Opcodes.BIPUSH, Opcodes.SIPUSH, Opcodes.LDC -> 0;

            default -> throw new IllegalStateException("Unexpected value: " + opcode);
        };
    }

    private static boolean isCommutative(int opcode) {
        if (opcode == Opcodes.IADD || opcode == Opcodes.LADD || opcode == Opcodes.FADD || opcode == Opcodes.DADD) return true;
        if (opcode == Opcodes.IMUL || opcode == Opcodes.LMUL || opcode == Opcodes.FMUL || opcode == Opcodes.DMUL) return true;

        // seems jagex doesn't flip bitwise operations
//        if (opcode == Opcodes.IAND || opcode == Opcodes.LAND) return true;
//        if (opcode == Opcodes.IOR || opcode == Opcodes.LOR) return true;
//        if (opcode == Opcodes.IXOR || opcode == Opcodes.LXOR) return true;
        return false;
    }

    private static Expression createExpression(AbstractInsnNode instruction, Deque<Expression> stack) {
        var opcode = instruction.getOpcode();

        switch (opcode) {
            case Opcodes.ACONST_NULL, Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2,
                    Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.LCONST_0, Opcodes.LCONST_1,
                    Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2, Opcodes.DCONST_0, Opcodes.DCONST_1 -> {
                return new Expression.NullaryExpression(opcode);
            }

            case Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG, Opcodes.I2L, Opcodes.I2F, Opcodes.I2D,
                    Opcodes.L2I, Opcodes.L2F, Opcodes.L2D, Opcodes.F2I, Opcodes.F2L, Opcodes.F2D, Opcodes.D2I,
                    Opcodes.D2L, Opcodes.D2F, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S, Opcodes.ARRAYLENGTH -> {
                if (stack.size() >= 1) {
                    var arg = stack.removeLast();
                    return new Expression.UnaryExpression(opcode, arg);
                } else {
                    return null;
                }
            }

            case Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD, Opcodes.BALOAD,
                    Opcodes.CALOAD, Opcodes.SALOAD, Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD,
                    Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB, Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL,
                    Opcodes.DMUL, Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV, Opcodes.IREM, Opcodes.LREM,
                    Opcodes.FREM, Opcodes.DREM, Opcodes.ISHL, Opcodes.ISHR, Opcodes.LSHL, Opcodes.IUSHR, Opcodes.LUSHR,
                    Opcodes.IAND, Opcodes.LAND, Opcodes.IOR, Opcodes.LOR, Opcodes.IXOR, Opcodes.LXOR, Opcodes.LCMP, Opcodes.FCMPL,
                    Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG -> {
                if (stack.size() >= 2) {
                    var arg1 = stack.removeLast();
                    var arg0 = stack.removeLast();
                    return new Expression.BinaryExpression(opcode, arg0, arg1);
                } else {
                    return null;
                }
            }

            case Opcodes.BIPUSH, Opcodes.SIPUSH -> {
                return new Expression.IntExpression(opcode, ((IntInsnNode) instruction).operand);
            }

            case Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> {
                return new Expression.LoadExpression(opcode, ((VarInsnNode) instruction).var);
            }

            case Opcodes.GETSTATIC -> {
                var fieldInsn = (FieldInsnNode) instruction;
                return new Expression.StaticInstruction(opcode, fieldInsn.owner, fieldInsn.name, fieldInsn.desc);
            }

            case Opcodes.GETFIELD -> {
                if (stack.size() >= 1) {
                    var arg = stack.removeLast();
                    var fieldInsn = (FieldInsnNode) instruction;
                    return new Expression.FieldInstruction(opcode, fieldInsn.owner, fieldInsn.name, fieldInsn.desc, arg);
                } else {
                    return null;
                }
            }

            case Opcodes.LDC -> {
                return new Expression.LdcExpression(opcode, ((LdcInsnNode) instruction).cst);
            }
        }

        return null;
    }

    private static void place(MethodNode method, Expression expression) {
        switch (expression) {
            case Expression.NullaryExpression(int opcode) -> {
                method.visitInsn(opcode);
            }

            case Expression.UnaryExpression(int opcode,Expression argument) -> {
                place(method, argument);
                method.visitInsn(opcode);
            }

            case Expression.BinaryExpression(int opcode,Expression argument1,Expression argument2) -> {
                if (isCommutative(opcode) && shouldSwap(argument1, argument2)) {
                    place(method, argument2);
                    place(method, argument1);
                } else {
                    place(method, argument1);
                    place(method, argument2);
                }

                method.visitInsn(opcode);
            }

            case Expression.IntExpression(int opcode,int operand) -> {
                method.visitIntInsn(opcode, operand);
            }

            case Expression.LdcExpression(int opcode,Object value) -> {
                method.visitLdcInsn(value);
            }

            case Expression.LoadExpression(int opcode,int var) -> {
                method.visitVarInsn(opcode, var);
            }

            case Expression.StaticInstruction(int opcode,String owner,String name,String desc) -> {
                method.visitFieldInsn(opcode, owner, name, desc);
            }

            case Expression.FieldInstruction(int opcode,String owner,String name,String desc,Expression argument) -> {
                place(method, argument);
                method.visitFieldInsn(opcode, owner, name, desc);
            }
        }
    }

    public sealed interface Expression {
        int opcode();

        record NullaryExpression(int opcode) implements Expression {}

        record UnaryExpression(int opcode, Expression argument) implements Expression {}

        record BinaryExpression(int opcode, Expression argument1, Expression argument2) implements Expression {}

        record IntExpression(int opcode, int operand) implements Expression {}

        record LdcExpression(int opcode, Object constant) implements Expression {}

        record LoadExpression(int opcode, int var) implements Expression {}

        record StaticInstruction(int opcode, String owner, String name, String desc) implements Expression {}

        record FieldInstruction(int opcode, String owner, String name, String desc, Expression argument) implements Expression {}
    }
}
