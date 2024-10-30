package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.runewiki.asm.transform.Transformer;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SortFieldsNameTransformer extends Transformer {
    private boolean unreliableClassOrder = false;

    @Override
    public void provide(TomlParseResult profile) {
        super.provide(profile);

        unreliableClassOrder = Boolean.TRUE.equals(profile.getBoolean("profile.deob.sort_fields.unreliable"));
    }

    @Override
    public void transform(List<ClassNode> classes) {
        List<String> names = new ArrayList<>();

        if (!unreliableClassOrder) {
            // Compute obfuscated name order for this build based on class order
            for (var clazz : classes) {
                var obfuscatedName = findObfuscatedName(clazz.invisibleAnnotations, clazz.visibleAnnotations);

                if (obfuscatedName != null) {
                    names.add(obfuscatedName);
                }
            }
        } else {
            // This should be generated based on the init order of the enum command class
            try {
                names = Files.readAllLines(Path.of("obforder.txt"));
            } catch (IOException ignore) {
            }
        }

        var nameIndices = computeIndexMap(names);

        for (var clazz : classes) {
            // Sort fields based on obfuscated name
            var fieldIndices = new HashMap<FieldNode, Integer>();

            for (var field : clazz.fields) {
                var fieldObf = findObfuscatedName(field.invisibleAnnotations, field.visibleAnnotations);

                if (fieldObf == null) {
                    fieldIndices.put(field, -1);
                } else {
                    fieldIndices.put(field, nameIndices.getOrDefault(fieldObf.substring(fieldObf.indexOf(".") + 1), Integer.MAX_VALUE));
                }
            }

            // As a fallback, sort based on order in initializer (to ensure correctness)
            var initAccessOrderList = new ArrayList<String>();

            for (var method : clazz.methods) {
                if (method.name.equals("<clinit>")) {
                    for (var instruction : method.instructions) {
                        if (instruction instanceof FieldInsnNode fieldInsn && (fieldInsn.getOpcode() == Opcodes.GETSTATIC || fieldInsn.getOpcode() == Opcodes.PUTSTATIC) && fieldInsn.owner.equals(clazz.name)) {
                            initAccessOrderList.add(fieldInsn.name);
                        }
                    }
                }

                if (method.name.equals("<init>")) {
                    for (var instruction : method.instructions) {
                        if (instruction instanceof FieldInsnNode fieldInsn && (fieldInsn.getOpcode() == Opcodes.GETFIELD || fieldInsn.getOpcode() == Opcodes.PUTFIELD) && fieldInsn.owner.equals(clazz.name)) {
                            initAccessOrderList.add(fieldInsn.name);
                        }
                    }
                }
            }

            var initAccessOrder = computeIndexMap(initAccessOrderList);

            clazz.fields.sort(Comparator
                    .<FieldNode>comparingInt(fieldIndices::get)
                    .thenComparingInt(f -> initAccessOrder.getOrDefault(f.name, Integer.MAX_VALUE))
                    .thenComparing(f -> f.desc)
                    .thenComparing(f -> f.name)
            );
        }
    }

    private static String findObfuscatedName(List<AnnotationNode> invisibleAnnotations, List<AnnotationNode> visibleAnnotations) {
        if (invisibleAnnotations != null) {
            for (var annotation : invisibleAnnotations) {
                if (annotation.desc.equals("LObfuscatedName;")) {
                    return ((String) annotation.values.get(1));
                }
            }
        }

        if (visibleAnnotations != null) {
            for (var annotation : visibleAnnotations) {
                if (annotation.desc.equals("LObfuscatedName;")) {
                    return ((String) annotation.values.get(1));
                }
            }
        }

        return null;
    }

    private static Map<String, Integer> computeIndexMap(List<String> list) {
        var indices = new HashMap<String, Integer>();

        for (var i = 0; i < list.size(); i++) {
            indices.putIfAbsent(list.get(i), i);
        }

        return indices;
    }
}
