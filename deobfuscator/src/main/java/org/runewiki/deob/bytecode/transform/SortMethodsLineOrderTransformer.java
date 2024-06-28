package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import org.runewiki.asm.transform.Transformer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class SortMethodsLineOrderTransformer extends Transformer {
    @Override
    public void transform(List<ClassNode> classes) {
        for (var clazz : classes) {
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
        }
    }
}
