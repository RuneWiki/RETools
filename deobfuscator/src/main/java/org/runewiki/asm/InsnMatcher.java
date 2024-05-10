package org.runewiki.asm;

import org.objectweb.asm.util.Printer;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.InsnList;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InsnMatcher {
    private final Pattern pattern;

    private InsnMatcher(Pattern pattern) {
        this.pattern = pattern;
    }

    public List<List<AbstractInsnNode>> match(MethodNode method) {
        return this.match(method.instructions);
    }

    public List<List<AbstractInsnNode>> match(InsnList list) {
        List<AbstractInsnNode> insns = new ArrayList<>(list.size());
        StringBuilder builder = new StringBuilder(list.size());

        for (AbstractInsnNode insn : list) {
            if (insn.getOpcode() != -1) {
                insns.add(insn);
                builder.append(opcodeToCodepoint(insn.getOpcode()));
            }
        }

        Matcher matcher = this.pattern.matcher(builder);
        List<List<AbstractInsnNode>> matches = new ArrayList<>();

        while (matcher.find()) {
            matches.add(insns.subList(matcher.start(), matcher.end()));
        }

        return matches;
    }

    private static final int PRIVATE_USE_AREA = 0xE000;

    private static char opcodeToCodepoint(int opcode) {
        return (char) (opcode + InsnMatcher.PRIVATE_USE_AREA);
    }

    private static void appendOpcodeRegex(StringBuilder pattern, String opcode) {
        for (int i = 0; i < Printer.OPCODES.length; i++) {
            if (Printer.OPCODES[i].equals(opcode)) {
                pattern.append(opcodeToCodepoint(i));
                return;
            }
        }

        // todo: OPCODE_GROUPS logic

        if (opcode.equals("AbstractInsnNode")) {
            pattern.append('.');
            return;
        }

        throw new IllegalArgumentException(opcode + " is not a valid opcode or opcode group");
    }

    public static InsnMatcher compile(String regex) {
        StringBuilder pattern = new StringBuilder();
        StringBuilder opcode = new StringBuilder();

        for (char c : regex.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_') {
                opcode.append(c);
            } else {
                if (opcode.length() > 0) {
                    InsnMatcher.appendOpcodeRegex(pattern, opcode.toString());
                    opcode.delete(0, opcode.length());
                }

                if (!Character.isWhitespace(c)) {
                    pattern.append(c);
                }
            }
        }

        if (opcode.length() > 0) {
            InsnMatcher.appendOpcodeRegex(pattern, opcode.toString());
            opcode.delete(0, opcode.length());
        }

        return new InsnMatcher(Pattern.compile(pattern.toString()));
    }
}
