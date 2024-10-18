package zwyz.deob;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.List;

public class LineNumberAdder {
    static void run(List<ClassNode> classes) {
        for (var clazz : classes) {
            for (var method : clazz.methods) {
                var instruction = method.instructions.getFirst();

                while (instruction != null) {
                    if (instruction instanceof LineNumberNode ln) {
                        method.instructions.insertBefore(instruction, new LdcInsnNode(ln.line));
                        method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC, "debug", "lineNumber", "(I)V"));
                    }

                    instruction = instruction.getNext();
                }
            }
        }
    }
}
