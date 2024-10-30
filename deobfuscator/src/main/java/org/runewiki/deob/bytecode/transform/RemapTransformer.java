package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.runewiki.asm.transform.Transformer;
import org.tomlj.TomlParseResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class RemapTransformer extends Transformer {
    private static String mappingFile = "remap.txt";
    private static String defaultPkg = "deob";

    @Override
    public void provide(TomlParseResult profile) {
        super.provide(profile);

        String file = profile.getString("profile.remap.file");
        if (file != null) {
            mappingFile = file.replaceAll("\\.", "/");
        }

        String pkg = profile.getString("profile.remap.default_package");
        if (pkg != null) {
            defaultPkg = pkg;
        }
    }

    @Override
    public void transform(List<ClassNode> classes) {
        var mappings = new HashMap<String, String>();

        try {
            for (var line : Files.readAllLines(Path.of(mappingFile))) {
                if (line.contains("#")) line = line.split("#")[0];
                line = line.trim();
                if (line.isBlank() || line.endsWith("=")) continue;
                if (!line.split("=")[0].contains(".")) line = line.replace(".", "/");
                mappings.put(line.split("=")[0], line.split("=")[1]);
            }
        } catch (Exception ex) {
            System.err.println("Failed to read mapping file: " + mappingFile);
        }

        var intermediaryMappings = new HashMap<String, String>();

        for (var clazz : classes) {
            var classMapping = mappings.get(findObfuscatedName(clazz.visibleAnnotations, clazz.invisibleAnnotations, clazz.name));
            var className = classMapping != null ? classMapping : clazz.name;
            var pkgName = className.substring(0, className.lastIndexOf('/') + 1);

            intermediaryMappings.put(clazz.name, pkgName.isEmpty() ? defaultPkg + "/" + className : className);

            for (var field : clazz.fields) {
                var fieldMapping = mappings.get(findObfuscatedName(field.visibleAnnotations, field.invisibleAnnotations, field.name));

                if (fieldMapping != null) {
                    intermediaryMappings.put(field.name, fieldMapping);
                }
            }

            for (var method : clazz.methods) {
                var methodMapping = mappings.get(findObfuscatedName(method.visibleAnnotations, method.invisibleAnnotations, method.name));

                if (methodMapping != null) {
                    intermediaryMappings.put(method.name, methodMapping);
                }
            }
        }

        intermediaryMappings.put("Statics", "deob/Statics");
        intermediaryMappings.put("ObfuscatedName", "deob/ObfuscatedName");
        intermediaryMappings.put("Moved", "deob/Moved");

        // Move statics
        var newOwners = new HashMap<String, String>();

        for (var key : new HashSet<>(intermediaryMappings.keySet())) {
            var value = intermediaryMappings.get(key);

            if (value.contains(",")) {
                var parts = value.split(",");
                newOwners.put(key, parts[0]);
                intermediaryMappings.put(key, parts[1]);
            }
        }

        moveStatics(classes, newOwners);

        // Re-sort by line numbers after moving statics and inners
        new SortMethodsTransformer().transform(classes);
        new SortFieldsNameTransformer().transform(classes);

        // Remap
        var mappedClasses = new ArrayList<ClassNode>();

        for (var clazz : classes) {
            var mappedClass = new ClassNode();

            clazz.accept(new ClassRemapper(mappedClass, new Remapper() {
                @Override
                public String map(String name) {
                    return intermediaryMappings.getOrDefault(name, name);
                }

                @Override
                public String mapMethodName(String owner, String name, String descriptor) {
                    if (name.startsWith("method")) {
                        return intermediaryMappings.getOrDefault(name, name);
                    } else {
                        return name;
                    }
                }

                @Override
                public String mapInvokeDynamicMethodName(String name, String descriptor) {
                    if (name.startsWith("method")) {
                        return intermediaryMappings.getOrDefault(name, name);
                    } else {
                        return name;
                    }
                }

                @Override
                public String mapAnnotationAttributeName(String descriptor, String name) {
                    return intermediaryMappings.getOrDefault(name, name);
                }

                @Override
                public String mapFieldName(String owner, String name, String descriptor) {
                    if (name.startsWith("field")) {
                        return intermediaryMappings.getOrDefault(name, name);
                    } else {
                        return name;
                    }
                }
            }));

            mappedClasses.add(mappedClass);
        }

        // Set source file info
        for (var clazz : mappedClasses) {
            var fileName = clazz.name;

            if (fileName.contains("$")) {
                fileName = fileName.substring(0, fileName.indexOf("$"));
            }

            clazz.sourceFile = fileName + ".java";
        }

        // Set inner class info
        var innerClasses = new HashMap<String, List<String>>();
        var innerClassesAccess = new HashMap<String, Integer>();

        for (var clazz : mappedClasses) {
            var lastInnerIndex = clazz.name.lastIndexOf('$');

            if (lastInnerIndex != -1) {
                innerClasses.computeIfAbsent(clazz.name.substring(0, lastInnerIndex), k -> new ArrayList<>()).add(clazz.name);
            }

            var access = clazz.access | Opcodes.ACC_STATIC; // todo: is | STATIC necessary

            for (var field : clazz.fields) {
                if (field.name.startsWith("this$")) {
                    access &= ~Opcodes.ACC_STATIC;
                }
            }

            innerClassesAccess.put(clazz.name, access);
        }

        for (var clazz : mappedClasses) {
            var inner = innerClasses.get(clazz.name);

            if (inner != null) {
                clazz.innerClasses = new ArrayList<>();

                for (var fullName : inner) {
                    var innerName = fullName.substring(fullName.lastIndexOf('$') + 1);
                    var access = innerClassesAccess.get(fullName);

                    if (innerName.matches("[0-9]+")) { // anonymous
                        clazz.innerClasses.add(new InnerClassNode(fullName, null, null, access));
                    } else {
                        clazz.innerClasses.add(new InnerClassNode(fullName, clazz.name, innerName, access));
                    }
                }
            }
        }

        // Make everything public (for now)
        for (var clazz : mappedClasses) {
            clazz.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
            clazz.access |= Opcodes.ACC_PUBLIC;

            for (var field : clazz.fields) {
                field.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
                field.access |= Opcodes.ACC_PUBLIC;
            }

            for (var method : clazz.methods) {
                method.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
                method.access |= Opcodes.ACC_PUBLIC;
            }
        }

        for (var clazz : mappedClasses) {
            if (clazz.name.endsWith("Statics")) {
                clazz.fields.sort(Comparator.comparing(f -> f.name));
            }
        }

        classes.clear();
        classes.addAll(mappedClasses);
    }

    private static void moveStatics(List<ClassNode> classes, HashMap<String, String> newOwners) {
        var classesByName = new HashMap<String, ClassNode>();

        for (var clazz : classes) {
            classesByName.put(findObfuscatedName(clazz.visibleAnnotations, clazz.invisibleAnnotations, clazz.name), clazz);
        }

        // Move members
        for (var clazz : classes) {
            for (var field : new ArrayList<>(clazz.fields)) {
                if (newOwners.containsKey(field.name)) {
                    if ((field.access & Opcodes.ACC_STATIC) == 0) {
                        throw new IllegalStateException("tried to move non-static field " + findObfuscatedName(field.visibleAnnotations, field.invisibleAnnotations, field.name));
                    }

                    var owner = classesByName.get(newOwners.get(field.name));
                    if (owner != null) {
                        clazz.fields.remove(field);
                        owner.fields.add(field);
                    }
                }
            }

            for (var method : new ArrayList<>(clazz.methods)) {
                if (newOwners.containsKey(method.name)) {
                    if ((method.access & Opcodes.ACC_STATIC) == 0) {
                        throw new IllegalStateException("tried to move non-static method " + findObfuscatedName(method.visibleAnnotations, method.invisibleAnnotations, method.name));
                    }

                    var owner = classesByName.get(newOwners.get(method.name));
                    if (owner != null) {
                        clazz.methods.remove(method);
                        owner.methods.add(method);
                    }
                }
            }
        }

        // Update references
        for (var clazz : classes) {
            for (var method : clazz.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode fieldInsn && newOwners.containsKey(fieldInsn.name)) {
                        var owner = classesByName.get(newOwners.get(fieldInsn.name));
                        if (owner != null) {
                            fieldInsn.owner = owner.name;
                        }
                    }

                    if (instruction instanceof MethodInsnNode methodInsn && newOwners.containsKey(methodInsn.name)) {
                        var owner = classesByName.get(newOwners.get(methodInsn.name));
                        if (owner != null) {
                            methodInsn.owner = owner.name;
                        }
                    }
                }
            }
        }
    }

    private static String findObfuscatedName(List<AnnotationNode> visibleAnnotations, List<AnnotationNode> invisibleAnnotations, String name) {
        if (visibleAnnotations == null && invisibleAnnotations == null) {
            return name;
        }

        if (visibleAnnotations != null) {
            for (var annotation : visibleAnnotations) {
                if (annotation.desc.equals("LObfuscatedName;")) {
                    return (String) annotation.values.get(1);
                }
            }
        }

        if (invisibleAnnotations != null) {
            for (var annotation : invisibleAnnotations) {
                if (annotation.desc.equals("LObfuscatedName;")) {
                    return (String) annotation.values.get(1);
                }
            }
        }

        return name;
    }
}
