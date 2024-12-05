package rs.lostcity.deob.bytecode.transform.openrs2;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import rs.lostcity.asm.InsnMatcher;
import rs.lostcity.asm.InsnNodeUtil;
import rs.lostcity.asm.transform.Transformer;

import java.util.List;
import java.util.Objects;

/**
 * A [Transformer] responsible for removing Jagex's exception tracing. Jagex
 * inserts a try/catch block around every method that catches
 * [RuntimeException]s, wraps them with a custom [RuntimeException]
 * implementation and re-throws them. The wrapped exception's message contains
 * the values of the method's arguments. While this is for debugging and not
 * obfuscation, it is clearly automatically-generated and thus we remove these
 * exception handlers too.
 */
public class ExceptionTracingTransformer extends Transformer {
    private final InsnMatcher CATCH_MATCHER = InsnMatcher.compile(
        """
        ASTORE? ALOAD?
        ((LDC INVOKESTATIC) | (NEW DUP)
        ((LDC INVOKESPECIAL) | (INVOKESPECIAL LDC INVOKEVIRTUAL))
        ((ILOAD | LLOAD | FLOAD | DLOAD | ALOAD | (ALOAD IFNULL LDC GOTO LDC) | BIPUSH | SIPUSH | LDC) INVOKEVIRTUAL)*
        INVOKEVIRTUAL? INVOKEVIRTUAL INVOKESTATIC)?
        (NEW DUP INVOKESPECIAL)? ATHROW
        """
    );

    private int tryCatches = 0;

    @Override
    public void preTransform(List<ClassNode> classes) {
        this.tryCatches = 0;
    }

    @Override
    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        var matches = this.CATCH_MATCHER.match(method);

        for (List<AbstractInsnNode> match : matches) {
            boolean foundTryCatch = method.tryCatchBlocks.removeIf(tryCatch ->
                Objects.equals(tryCatch.type, "java/lang/RuntimeException") &&
                InsnNodeUtil.getNextReal(tryCatch.handler).equals(match.get(0)));

            if (foundTryCatch) {
                match.forEach(method.instructions::remove);
                this.tryCatches++;
            }
        }

        return false;
    }

    @Override
    public void postTransform(List<ClassNode> classes) {
        System.out.println("Removed " + this.tryCatches + " tracing try/catch blocks");
    }
}
