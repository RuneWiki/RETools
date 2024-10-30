package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.runewiki.asm.transform.Transformer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class StaticFieldsTransformer extends Transformer {
    @Override
    public void transform(List<ClassNode> classes) {
        // Remove unused fields
        var usedFields = new HashSet<String>();

        for (var clazz : classes) {
            if (ZwyzLegacyLogic.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
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
            if (ZwyzLegacyLogic.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
                continue;
            }

            clazz.fields.removeIf(f -> !usedFields.contains(f.name));
        }

        // Move static fields
        var unmovedFields = new HashSet<String>();

        for (var clazz : classes) {
            if (ZwyzLegacyLogic.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
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
            if (ZwyzLegacyLogic.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
                continue;
            }

            if (clazz.name.equals("statics")) {
                continue;
            }

            for (var field : new ArrayList<>(clazz.fields)) {
                if ((field.access & Opcodes.ACC_STATIC) != 0 && !unmovedFields.contains(field.name)) {
                    movedFields.add(field.name);
                    clazz.fields.remove(field);
                    ZwyzLegacyLogic.staticsClass.fields.add(field);
                }
            }
        }

        for (var clazz : classes) {
            if (ZwyzLegacyLogic.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
                continue;
            }

            for (var method : clazz.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode fieldInsn && (fieldInsn.getOpcode() == Opcodes.GETSTATIC || fieldInsn.getOpcode() == Opcodes.PUTSTATIC) && movedFields.contains(fieldInsn.name)) {
                        fieldInsn.owner = ZwyzLegacyLogic.staticsClass.name;
                    }
                }
            }
        }
    }
}
