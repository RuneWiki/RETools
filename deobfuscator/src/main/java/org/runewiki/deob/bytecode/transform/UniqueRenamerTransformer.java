package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.deob.bytecode.AsmUtil;
import org.tomlj.TomlParseResult;

import java.util.*;

public class UniqueRenamerTransformer extends Transformer {
    private boolean startAtZero;

    @Override
    public void provide(TomlParseResult profile) {
        super.provide(profile);

        startAtZero = Boolean.TRUE.equals(profile.getBoolean("profile.deob.unique_renamer.start_at_zero"));
    }

    @Override
    public void transform(List<ClassNode> classes) {
        var inheriting = computeInheritance(classes);
        var linkedMethods = computeLinkedMethods(classes, inheriting);
        var linkedFields = computeLinkedFields(classes, inheriting);

        var map = new HashMap<String, String>();
        var classCounter = 0;
        var fieldCounter = 0;
        var methodCounter = 0;

        if (startAtZero) {
            // we're subtracting the above to be -1 since we do "++counter" later, starting at class0 instead of class1
            classCounter--;
            fieldCounter--;
            methodCounter--;
        }

        for (var clazz : classes) {
            if (ZwyzLegacyLogic.EXTERNAL_LIBRARIES.stream().anyMatch(p -> clazz.name.startsWith(p))) {
                map.put(clazz.name, clazz.name);
                continue;
            }

            var packageName = clazz.name.substring(0, clazz.name.lastIndexOf('/') + 1);
            var className = clazz.name.substring(clazz.name.lastIndexOf('/') + 1);

            if (AsmUtil.isClassObfuscated(className)) {
                var newName = packageName + "class" + ++classCounter;
                map.put(clazz.name, newName);
            } else {
                map.put(clazz.name, clazz.name);
            }

            for (var field : clazz.fields) {
                if (AsmUtil.isFieldObfuscated(field.name)) {
                    var key = clazz.name + "." + field.name + ":" + field.desc;
                    var newName = "field" + ++fieldCounter;

                    for (var linked : linkedFields.getOrDefault(key, Set.of(key))) {
                        map.put(linked, newName);
                    }
                }
            }

            for (var method : clazz.methods) {
                if (AsmUtil.isMethodObfuscated(method.name)) {
                    var key = clazz.name + "." + method.name + method.desc;

                    if (!map.containsKey(key)) {
                        var renamed = "method" + ++methodCounter;

                        for (var linked : linkedMethods.getOrDefault(key, Set.of(key))) {
                            map.put(linked, renamed);
                        }
                    }
                }
            }
        }

        var remappedClasses = new ArrayList<ClassNode>();

        for (var clazz : classes) {
            var remapped = new ClassNode();
            clazz.accept(new ClassRemapper(remapped, new SimpleRemapper(map) {
                @Override
                public String mapFieldName(String owner, String name, String descriptor) {
                    return map.getOrDefault(owner + "." + name + ":" + descriptor, name);
                }
            }));
            remappedClasses.add(remapped);
        }

        classes.clear();
        classes.addAll(remappedClasses);
    }

    // todo: don't link static fields in classes that may share a name with a class that inherits it
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
                            var superFieldName = inheritedClass.name + "." + field.name + ":" + field.desc;
                            var fieldName = clazz.name + "." + field.name + ":" + field.desc;

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
}
