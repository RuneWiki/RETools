package rs.lostcity.deob.bytecode.transform.zwyz;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import rs.lostcity.asm.transform.Transformer;
import rs.lostcity.deob.bytecode.AsmUtil;

import java.util.List;

public class UnreachableCodeTransformer extends Transformer {
    @Override
    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        AsmUtil.removeUnreachableCode(method);

        return false;
    }
}
