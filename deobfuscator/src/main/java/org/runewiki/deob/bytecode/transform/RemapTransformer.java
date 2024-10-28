package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.deob.bytecode.remap.SimpleObfRemapper;
import org.runewiki.deob.AsmUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class RemapTransformer extends Transformer {
    @Override
    public void preTransform(List<ClassNode> classes) {
        var inheriting = computeInheritance(classes);
        var linkedMethods = computeLinkedMethods(classes, inheriting);
        var linkedFields = computeLinkedFields(classes, inheriting);

        var remap = new HashMap<String, String>();
        var classCounter = 0;
        var fieldCounter = 0;
        var methodCounter = 0;

        for (var clazz : classes) {
            var packageName = clazz.name.substring(0, clazz.name.lastIndexOf('/') + 1);
            var className = clazz.name.substring(clazz.name.lastIndexOf('/') + 1);

            if (AsmUtil.isClassObfuscated(className)) {
                var newName = packageName + "class" + ++classCounter;
                remap.put(clazz.name, newName);
            } else {
                remap.put(clazz.name, clazz.name);
            }

            for (var field : clazz.fields) {
                if (AsmUtil.isFieldObfuscated(field.name)) {
                    var key = clazz.name + "." + field.name + field.desc;
                    var newName = "field" + ++fieldCounter;

                    for (var linked : linkedFields.getOrDefault(key, Set.of(key))) {
                        remap.put(linked, newName);
                    }
                }
            }

            for (var method : clazz.methods) {
                if (AsmUtil.isMethodObfuscated(method.name)) {
                    var key = clazz.name + "." + method.name + method.desc;

                    if (!remap.containsKey(key)) {
                        var renamed = "method" + ++methodCounter;

                        for (var linked : linkedMethods.getOrDefault(key, Set.of(key))) {
                            remap.put(linked, renamed);
                        }
                    }
                }
            }
        }

        var newOwners = new HashMap<String, String>();

        // load existing mappings and merge
        try {
            Path path = Paths.get("remap.txt");
            if (Files.exists(path)) {
                Files.lines(path).forEach(line -> {
                    String[] parts = line.split("=");

                    var oldFqn = parts[0];
                    var newName = parts[1];

                    if (oldFqn.contains(".") && newName.contains(".")) {
                        // moving members to a new class
                        var owner = newName.substring(0, newName.lastIndexOf("."));
                        newName = newName.substring(newName.lastIndexOf(".") + 1);
                        newOwners.put(oldFqn, owner);
                    }

                    remap.put(oldFqn, newName);
                });
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // save combined mappings to file
        try {
            Path path = Paths.get("remap.txt");
            BufferedWriter writer = Files.newBufferedWriter(path);

            List<String> keys = new ArrayList<>(remap.keySet());
            for (String key : keys) {
                writer.write(key + "=" + remap.get(key));
                writer.newLine();
            }

            writer.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        var remapper = new SimpleObfRemapper(remap);
        var remappedClasses = new ArrayList<ClassNode>();

        for (var clazz : classes) {
            var remapped = new ClassNode();
            clazz.accept(new ClassRemapper(remapped, remapper));
            remappedClasses.add(remapped);
        }

        classes.clear();
        classes.addAll(remappedClasses);
        moveStatics(classes, newOwners, remapper);
    }

    private static Map<String, Set<String>> computeLinkedFields(List<ClassNode> classes, Map<String, Set<String>> inheriting) {
        var classesByName = new HashMap<String, ClassNode>();

        for (var clazz : classes) {
            classesByName.put(clazz.name, clazz);
        }

        var result = new LinkedHashMap<String, Set<String>>();

        for (var clazz : classes) {
            for (var inheritedName : inheriting.get(clazz.name)) {
                var inheritedClass = classesByName.get(inheritedName);

                if (inheritedClass != null) {
                    for (var field : inheritedClass.fields) {
                        if ((field.access & Opcodes.ACC_PRIVATE) == 0) {
                            var superFieldName = inheritedClass.name + "." + field.name + field.desc;
                            var fieldName = clazz.name + "." + field.name + field.desc;

                            merge(result, superFieldName, fieldName);
                        }
                    }
                }
            }
        }

        return result;
    }

    public static Map<String, Set<String>> computeLinkedMethods(Collection<ClassNode> classes, Map<String, Set<String>> inheriting) {
        var classesByName = new HashMap<String, ClassNode>();

        for (var clazz : classes) {
            classesByName.put(clazz.name, clazz);
        }

        var result = new LinkedHashMap<String, Set<String>>();

        for (var clazz : classes) {
            for (var inheritedName : inheriting.get(clazz.name)) {
                var inheritedClass = classesByName.get(inheritedName);

                if (inheritedClass != null) {
                    for (var method : inheritedClass.methods) {
                        if ((method.access & Opcodes.ACC_PRIVATE) == 0 && !method.name.equals("<init>")) {
                            var superMethodName = inheritedClass.name + "." + method.name + method.desc;
                            var methodName = clazz.name + "." + method.name + method.desc;

                            merge(result, superMethodName, methodName);
                        }
                    }
                }
            }
        }

        return result;
    }

    public static Map<String, Set<String>> computeInheritance(Collection<ClassNode> classes) {
        var inheriting = new HashMap<String, Set<String>>();

        for (var clazz : classes) {
            var set = inheriting.computeIfAbsent(clazz.name, k -> new LinkedHashSet<>());

            set.add(clazz.name);
            set.add(clazz.superName);
            set.addAll(clazz.interfaces);
        }

        return transitiveClosure(inheriting);
    }

    private static <T> void merge(LinkedHashMap<T, Set<T>> map, T a, T b) {
        var merged = new LinkedHashSet<T>();
        merged.addAll(map.getOrDefault(a, Set.of(a)));
        merged.addAll(map.getOrDefault(b, Set.of(b)));

        for (var o : merged) {
            map.put(o, merged);
        }
    }

    private static <T> Map<T, Set<T>> transitiveClosure(Map<T, Set<T>> map) {
        var result = new LinkedHashMap<T, Set<T>>();

        for (var key : map.keySet()) {
            result.put(key, descendants(map, key));
        }

        return result;
    }

    private static <T> Set<T> descendants(Map<T, Set<T>> ts, T t) {
        var result = new LinkedHashSet<T>();
        collectDescendants(ts, t, result);
        return result;
    }

    private static <T> void collectDescendants(Map<T, Set<T>> ts, T t, Set<T> result) {
        result.add(t);

        for (var child : ts.getOrDefault(t, Set.of())) {
            if (!result.contains(child)) {
                collectDescendants(ts, child, result);
            }
        }
    }

    // todo: move static initializers from <clinit> to new class
    // todo: support inner classes
    private static void moveStatics(List<ClassNode> classes, HashMap<String, String> newOwners, SimpleObfRemapper remapper) {
        if (newOwners.isEmpty()) {
            return;
        }

        var classesByName = new HashMap<String, ClassNode>();
        for (var clazz : classes) {
            classesByName.put(remapper.reverse.get(clazz.name), clazz);
        }

        // Move members
        for (var clazz : classes) {
            for (var field : new ArrayList<>(clazz.fields)) {
                var fqn = remapper.reverse.get(clazz.name + "." + field.name + field.desc);

                if (newOwners.containsKey(fqn)) {
                    if ((field.access & Opcodes.ACC_STATIC) == 0) {
                        throw new IllegalStateException("tried to move non-static field " + fqn);
                    }

                    clazz.fields.remove(field);
                    classesByName.get(newOwners.get(fqn)).fields.add(field);
                }
            }

            for (var method : new ArrayList<>(clazz.methods)) {
                var fqn = remapper.reverse.get(clazz.name + "." + method.name + method.desc);

                if (newOwners.containsKey(fqn)) {
                    if ((method.access & Opcodes.ACC_STATIC) == 0) {
                        throw new IllegalStateException("tried to move non-static method " + fqn);
                    }

                    clazz.methods.remove(method);
                    classesByName.get(newOwners.get(fqn)).methods.add(method);
                }
            }
        }

        // Update references
        for (var clazz : classes) {
            for (var method : clazz.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode insn) {
                        var fqn = remapper.reverse.get(insn.owner + "." + insn.name + insn.desc);

                        if (newOwners.containsKey(fqn)) {
                            var newOwner = classesByName.get(newOwners.get(fqn));
                            insn.owner = newOwner.name;
                        }
                    } else if (instruction instanceof MethodInsnNode insn) {
                        var fqn = remapper.reverse.get(insn.name + "." + insn.name + insn.desc);

                        if (newOwners.containsKey(fqn)) {
                            insn.owner = classesByName.get(newOwners.get(fqn)).name;
                        }
                    }
                }
            }
        }
    }
}
