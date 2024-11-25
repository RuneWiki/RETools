package rs.lostcity.deob.bytecode;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public class StringConvertingMethodVisitor extends MethodVisitor {
    protected final List<String> result = new ArrayList<>();
    protected final Map<Label, String> labelNames = new HashMap<>();
    protected int labelNameCounter = 0;

    public StringConvertingMethodVisitor() {
        super(Opcodes.ASM9);
    }

    public List<String> getResult() {
        return result;
    }

    @Override
    public void visitLineNumber(int line, Label start) {
//        result.add("line " + line);
    }

    @Override
    public void visitInsn(int opcode) {
        result.add(opcodeName(opcode));
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        result.add(opcodeName(opcode) + " " + operand);
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
        result.add(opcodeName(opcode) + " " + varIndex);
    }

    @Override public void visitTypeInsn(int opcode, String type) {
        result.add(opcodeName(opcode) + " " + type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        result.add(opcodeName(opcode) + " " + owner + " " + name + " " + descriptor);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        result.add(opcodeName(opcode) + " " + owner + " " + name + " " + descriptor);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        result.add(opcodeName(INVOKEDYNAMIC) + " " + name + " " + descriptor + " " + bootstrapMethodHandle + " " + Arrays.toString(bootstrapMethodArguments));
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        result.add(opcodeName(opcode) + " " + labelName(label));
    }

    @Override
    public void visitLabel(Label label) {
        result.add(labelName(label) + ":");
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        result.add(opcodeName(IINC) + " " + varIndex + " " + increment);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        result.add(opcodeName(TABLESWITCH) + " " + min + " " + max + " " + labelName(dflt) + " " + Arrays.toString(Arrays.stream(labels).map(this::labelName).toArray()));
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        result.add(opcodeName(LOOKUPSWITCH) + " " + labelName(dflt) + " " + Arrays.toString(keys) + " " + Arrays.toString(Arrays.stream(labels).map(this::labelName).toArray()));
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        result.add(opcodeName(MULTIANEWARRAY) + " " + descriptor + " " + numDimensions);
    }

    @Override
    public void visitLdcInsn(Object value) {
        result.add(opcodeName(LDC) + " " + value);
    }

    private String labelName(Label label) {
        return labelNames.computeIfAbsent(label, l -> "label_" + labelNameCounter++);
    }

    private String opcodeName(int opcode) {
        return (switch (opcode) {
            case NOP -> "NOP";
            case ACONST_NULL -> "ACONST_NULL";
            case ICONST_M1 -> "ICONST_M1";
            case ICONST_0 -> "ICONST_0";
            case ICONST_1 -> "ICONST_1";
            case ICONST_2 -> "ICONST_2";
            case ICONST_3 -> "ICONST_3";
            case ICONST_4 -> "ICONST_4";
            case ICONST_5 -> "ICONST_5";
            case LCONST_0 -> "LCONST_0";
            case LCONST_1 -> "LCONST_1";
            case FCONST_0 -> "FCONST_0";
            case FCONST_1 -> "FCONST_1";
            case FCONST_2 -> "FCONST_2";
            case DCONST_0 -> "DCONST_0";
            case DCONST_1 -> "DCONST_1";
            case BIPUSH -> "BIPUSH";
            case SIPUSH -> "SIPUSH";
            case LDC -> "LDC";
            case ILOAD -> "ILOAD";
            case LLOAD -> "LLOAD";
            case FLOAD -> "FLOAD";
            case DLOAD -> "DLOAD";
            case ALOAD -> "ALOAD";
            case IALOAD -> "IALOAD";
            case LALOAD -> "LALOAD";
            case FALOAD -> "FALOAD";
            case DALOAD -> "DALOAD";
            case AALOAD -> "AALOAD";
            case BALOAD -> "BALOAD";
            case CALOAD -> "CALOAD";
            case SALOAD -> "SALOAD";
            case ISTORE -> "ISTORE";
            case LSTORE -> "LSTORE";
            case FSTORE -> "FSTORE";
            case DSTORE -> "DSTORE";
            case ASTORE -> "ASTORE";
            case IASTORE -> "IASTORE";
            case LASTORE -> "LASTORE";
            case FASTORE -> "FASTORE";
            case DASTORE -> "DASTORE";
            case AASTORE -> "AASTORE";
            case BASTORE -> "BASTORE";
            case CASTORE -> "CASTORE";
            case SASTORE -> "SASTORE";
            case POP -> "POP";
            case POP2 -> "POP2";
            case DUP -> "DUP";
            case DUP_X1 -> "DUP_X1";
            case DUP_X2 -> "DUP_X2";
            case DUP2 -> "DUP2";
            case DUP2_X1 -> "DUP2_X1";
            case DUP2_X2 -> "DUP2_X2";
            case SWAP -> "SWAP";
            case IADD -> "IADD";
            case LADD -> "LADD";
            case FADD -> "FADD";
            case DADD -> "DADD";
            case ISUB -> "ISUB";
            case LSUB -> "LSUB";
            case FSUB -> "FSUB";
            case DSUB -> "DSUB";
            case IMUL -> "IMUL";
            case LMUL -> "LMUL";
            case FMUL -> "FMUL";
            case DMUL -> "DMUL";
            case IDIV -> "IDIV";
            case LDIV -> "LDIV";
            case FDIV -> "FDIV";
            case DDIV -> "DDIV";
            case IREM -> "IREM";
            case LREM -> "LREM";
            case FREM -> "FREM";
            case DREM -> "DREM";
            case INEG -> "INEG";
            case LNEG -> "LNEG";
            case FNEG -> "FNEG";
            case DNEG -> "DNEG";
            case ISHL -> "ISHL";
            case LSHL -> "LSHL";
            case ISHR -> "ISHR";
            case LSHR -> "LSHR";
            case IUSHR -> "IUSHR";
            case LUSHR -> "LUSHR";
            case IAND -> "IAND";
            case LAND -> "LAND";
            case IOR -> "IOR";
            case LOR -> "LOR";
            case IXOR -> "IXOR";
            case LXOR -> "LXOR";
            case IINC -> "IINC";
            case I2L -> "I2L";
            case I2F -> "I2F";
            case I2D -> "I2D";
            case L2I -> "L2I";
            case L2F -> "L2F";
            case L2D -> "L2D";
            case F2I -> "F2I";
            case F2L -> "F2L";
            case F2D -> "F2D";
            case D2I -> "D2I";
            case D2L -> "D2L";
            case D2F -> "D2F";
            case I2B -> "I2B";
            case I2C -> "I2C";
            case I2S -> "I2S";
            case LCMP -> "LCMP";
            case FCMPL -> "FCMPL";
            case FCMPG -> "FCMPG";
            case DCMPL -> "DCMPL";
            case DCMPG -> "DCMPG";
            case IFEQ -> "IFEQ";
            case IFNE -> "IFNE";
            case IFLT -> "IFLT";
            case IFGE -> "IFGE";
            case IFGT -> "IFGT";
            case IFLE -> "IFLE";
            case IF_ICMPEQ -> "IF_ICMPEQ";
            case IF_ICMPNE -> "IF_ICMPNE";
            case IF_ICMPLT -> "IF_ICMPLT";
            case IF_ICMPGE -> "IF_ICMPGE";
            case IF_ICMPGT -> "IF_ICMPGT";
            case IF_ICMPLE -> "IF_ICMPLE";
            case IF_ACMPEQ -> "IF_ACMPEQ";
            case IF_ACMPNE -> "IF_ACMPNE";
            case GOTO -> "GOTO";
            case JSR -> "JSR";
            case RET -> "RET";
            case TABLESWITCH -> "TABLESWITCH";
            case LOOKUPSWITCH -> "LOOKUPSWITCH";
            case IRETURN -> "IRETURN";
            case LRETURN -> "LRETURN";
            case FRETURN -> "FRETURN";
            case DRETURN -> "DRETURN";
            case ARETURN -> "ARETURN";
            case RETURN -> "RETURN";
            case GETSTATIC -> "GETSTATIC";
            case PUTSTATIC -> "PUTSTATIC";
            case GETFIELD -> "GETFIELD";
            case PUTFIELD -> "PUTFIELD";
            case INVOKEVIRTUAL -> "INVOKEVIRTUAL";
            case INVOKESPECIAL -> "INVOKESPECIAL";
            case INVOKESTATIC -> "INVOKESTATIC";
            case INVOKEINTERFACE -> "INVOKEINTERFACE";
            case INVOKEDYNAMIC -> "INVOKEDYNAMIC";
            case NEW -> "NEW";
            case NEWARRAY -> "NEWARRAY";
            case ANEWARRAY -> "ANEWARRAY";
            case ARRAYLENGTH -> "ARRAYLENGTH";
            case ATHROW -> "ATHROW";
            case CHECKCAST -> "CHECKCAST";
            case INSTANCEOF -> "INSTANCEOF";
            case MONITORENTER -> "MONITORENTER";
            case MONITOREXIT -> "MONITOREXIT";
            case MULTIANEWARRAY -> "MULTIANEWARRAY";
            case IFNULL -> "IFNULL";
            case IFNONNULL -> "IFNONNULL";
            default -> throw new IllegalStateException();
        }).toLowerCase(Locale.ROOT);
    }
}
