package zwyz.deob;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class StaticFields {
    static void run(List<ClassNode> classes, ClassNode staticsClass, ClassNode movedAnnotationClass) {
        // Remove unused fields
        var usedFields = new HashSet<String>();

        for (var clazz : classes) {
            if (ZwyzDeobStep1.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
                continue;
            }

            for (var method : clazz.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode fieldInsn) {
                        usedFields.add(fieldInsn.name);
                    }
                }
            }
        }

        for (var clazz : classes) {
            if (ZwyzDeobStep1.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
                continue;
            }

            clazz.fields.removeIf(f -> !usedFields.contains(f.name));
        }

        // Move static fields
        var unmovedFields = new HashSet<String>();

        for (var clazz : classes) {
            if (ZwyzDeobStep1.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
                continue;
            }

            for (var method : clazz.methods) {
                if (method.name.equals("<clinit>")) {
                    for (var instruction : method.instructions) {
                        if (instruction instanceof FieldInsnNode fieldInsn) {
                            unmovedFields.add(fieldInsn.name);
                        }
                    }
                }
            }
        }

        var movedFields = new HashSet<String>();

        for (var clazz : classes) {
            if (ZwyzDeobStep1.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
                continue;
            }

            if (clazz.name.equals("statics")) {
                continue;
            }

            for (var field : new ArrayList<>(clazz.fields)) {
                if ((field.access & Opcodes.ACC_STATIC) != 0 && !unmovedFields.contains(field.name)) {
                    movedFields.add(field.name);
                    clazz.fields.remove(field);
                    staticsClass.fields.add(field);

                    if (ZwyzDeobStep1.TRACK_MOVED) {
                        field.visitAnnotation("L" + movedAnnotationClass.name + ";", false).visitEnd();
                    }
                }
            }
        }

        for (var clazz : classes) {
            if (ZwyzDeobStep1.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
                continue;
            }

            for (var method : clazz.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode fieldInsn && (fieldInsn.getOpcode() == Opcodes.GETSTATIC || fieldInsn.getOpcode() == Opcodes.PUTSTATIC) && movedFields.contains(fieldInsn.name)) {
                        fieldInsn.owner = staticsClass.name;
                    }
                }
            }
        }
    }
}
