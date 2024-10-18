package zwyz.deob;

import org.objectweb.asm.tree.InsnList;

import java.util.*;

public class CodeString {
    public static List<String> compute(InsnList instructions) {
        var mv = new StringConvertingMethodVisitor();
        instructions.accept(mv);
        return mv.getResult();
    }

}
