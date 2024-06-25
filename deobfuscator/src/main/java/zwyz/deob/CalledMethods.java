package zwyz.deob;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.LinkedHashSet;
import java.util.List;

public class CalledMethods {
    public static LinkedHashSet<String> run(List<ClassNode> classes) {
        var calledMethods = new LinkedHashSet<String>();

        for (var clazz : classes) {
            for (var method : clazz.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode mi) {
                        calledMethods.add(mi.name);
                    }

                    if (instruction instanceof InvokeDynamicInsnNode id) {
                        if (id.bsmArgs[1] instanceof Handle handle) {
                            calledMethods.add(handle.getName());
                        }
                    }
                }
            }
        }

        for (var clazz : classes) {
            for (var method : clazz.methods) {
                if (!method.name.startsWith("method")) {
                    calledMethods.add(method.name);
                }
            }
        }

        return calledMethods;
    }
}
