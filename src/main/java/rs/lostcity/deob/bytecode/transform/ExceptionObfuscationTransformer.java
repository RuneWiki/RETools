package rs.lostcity.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import rs.lostcity.asm.InsnNodeUtil;
import rs.lostcity.asm.transform.Transformer;

import java.util.List;

/**
 * A [Transformer] responsible for removing [ZKM](http://www.zelix.com/klassmaster/)'s
 * [exception obfuscation](https://www.zelix.com/klassmaster/featuresExceptionObfuscation.html),
 * which inserts exception handlers that catch any type of exception and
 * immediately re-throw them. The exception handlers are inserted in locations
 * where there is no Java source code equivalent, confusing decompilers.
 */
public class ExceptionObfuscationTransformer extends Transformer {
    private int handlers = 0;

    @Override
    public void preTransform(List<ClassNode> classes) {
        this.handlers = 0;
    }

    @Override
    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        for (var insn : method.instructions) {
            if (insn.getOpcode() != Opcodes.ATHROW) {
                continue;
            }

            var foundTryCatch = method.tryCatchBlocks.removeIf(tryCatch ->
                InsnNodeUtil.getNextReal(tryCatch.handler).equals(insn));

            if (foundTryCatch) {
                method.instructions.remove(insn);
                handlers++;
            }
        }

        return false;
    }

    @Override
    public void postTransform(List<ClassNode> classes) {
        System.out.println("Removed " + this.handlers + " exception obfuscation handlers");
    }
}
