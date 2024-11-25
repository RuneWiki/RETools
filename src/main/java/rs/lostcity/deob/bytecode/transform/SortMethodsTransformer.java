package rs.lostcity.deob.bytecode.transform;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import rs.lostcity.asm.transform.Transformer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class SortMethodsTransformer extends Transformer {
    @Override
    public boolean transformClass(List<ClassNode> classes, ClassNode clazz) {
        // Sort methods based on line numbers
        var lines = new HashMap<MethodNode, Integer>();

        for (var method : clazz.methods) {
            var min = Integer.MAX_VALUE;

            for (var instruction : method.instructions) {
                if (instruction instanceof LineNumberNode lineNumber && lineNumber.line < min) {
                    min = lineNumber.line;
                }
            }

            lines.put(method, min);
        }

        clazz.methods.sort(Comparator.comparingInt(lines::get));

        return false;
    }
}
