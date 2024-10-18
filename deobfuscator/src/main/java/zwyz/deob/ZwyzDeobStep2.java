package zwyz.deob;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZwyzDeobStep2 {
    public static void run(Path MAPPINGS, Path INPUT, Path OUTPUT, String defaultPackage) throws IOException {
        var classes = readClasses(INPUT);
        var mappings = new HashMap<String, String>();

        for (var line : Files.readAllLines(MAPPINGS)) {
            if (line.contains("#")) line = line.split("#")[0];
            line = line.trim();
            if (line.isBlank() || line.endsWith("=")) continue;
            if (!line.split("=")[0].contains(".")) line = line.replace(".", "/");
            mappings.put(line.split("=")[0], line.split("=")[1]);
        }

        if (defaultPackage == null) {
            defaultPackage = "deob";
        }

        var intermediaryMappings = new HashMap<String, String>();

        for (var clazz : classes) {
            var classMapping = mappings.get(findObfuscatedName(clazz.visibleAnnotations, clazz.name));

            if (classMapping != null) {
                intermediaryMappings.put(clazz.name, classMapping);
            } else if (!clazz.name.contains("/")) {
                intermediaryMappings.put(clazz.name, defaultPackage + "/" + clazz.name);
            }

            for (var field : clazz.fields) {
                var fieldMapping = mappings.get(findObfuscatedName(field.visibleAnnotations, field.name));

                if (fieldMapping != null) {
                    intermediaryMappings.put(field.name, fieldMapping);
                }
            }

            for (var method : clazz.methods) {
                var methodMapping = mappings.get(findObfuscatedName(method.visibleAnnotations, method.name));

                if (methodMapping != null) {
                    intermediaryMappings.put(method.name, methodMapping);
                }
            }
        }

        intermediaryMappings.put("statics", defaultPackage + "/Statics");
        intermediaryMappings.put("ObfuscatedName", defaultPackage + "/ObfuscatedName");
        intermediaryMappings.put("Moved", defaultPackage + "/Moved");

        // Move statics
        var newOwners = new HashMap<String, String>();

        for (var key : new HashSet<>(intermediaryMappings.keySet())) {
            var value = intermediaryMappings.get(key);

            if (value.contains(",")) {
                var parts = value.split(",");
                newOwners.put(key, parts[0]);

                if (!value.endsWith(",")) {
                    intermediaryMappings.put(key, parts[1]);
                } else {
                    intermediaryMappings.remove(key);
                }
            }
        }

        moveStatics(classes, newOwners);

        // Re-sort by line numbers after moving statics and inners
        SortMethods.run(classes);
        SortFieldsName.run(classes);

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

//        LineNumberAdder.run(mappedClasses);
        writeClasses(OUTPUT, mappedClasses);
    }

    private static void moveStatics(List<ClassNode> classes, HashMap<String, String> newOwners) {
        var classesByName = new HashMap<String, ClassNode>();

        for (var clazz : classes) {
            classesByName.put(findObfuscatedName(clazz.visibleAnnotations, clazz.name), clazz);
        }

        // Move members
        for (var clazz : classes) {
            for (var field : new ArrayList<>(clazz.fields)) {
                if (newOwners.containsKey(field.name)) {
                    if ((field.access & Opcodes.ACC_STATIC) == 0) {
                        throw new IllegalStateException("tried to move non-static field " + findObfuscatedName(field.visibleAnnotations, field.name));
                    }

                    clazz.fields.remove(field);
                    classesByName.get(newOwners.get(field.name)).fields.add(field);
                }
            }

            for (var method : new ArrayList<>(clazz.methods)) {
                if (newOwners.containsKey(method.name)) {

                    if ((method.access & Opcodes.ACC_STATIC) == 0) {
                        throw new IllegalStateException("tried to move non-static method " + findObfuscatedName(method.visibleAnnotations, method.name));
                    }

                    clazz.methods.remove(method);
                    classesByName.get(newOwners.get(method.name)).methods.add(method);
                }
            }
        }

        // Update references
        for (var clazz : classes) {
            for (var method : clazz.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode fieldInsn && newOwners.containsKey(fieldInsn.name)) {
                        fieldInsn.owner = classesByName.get(newOwners.get(fieldInsn.name)).name;
                    }

                    if (instruction instanceof MethodInsnNode methodInsn && newOwners.containsKey(methodInsn.name)) {
                        methodInsn.owner = classesByName.get(newOwners.get(methodInsn.name)).name;
                    }
                }
            }
        }
    }

    private static String findObfuscatedName(List<AnnotationNode> annotations, String name) {
        if (annotations == null) {
            return name;
        }

        for (var annotation : annotations) {
            if (annotation.desc.equals("LObfuscatedName;")) {
                return (String) annotation.values.get(1);
            }
        }

        return name;
    }

    private static List<ClassNode> readClasses(Path path) throws IOException {
        var classes = new ArrayList<ClassNode>();

        try (var zin = new ZipInputStream(Files.newInputStream(path))) {
            while (true) {
                var entry = zin.getNextEntry();

                if (entry == null) {
                    break;
                }

                var string = entry.getName();

                if (string.endsWith(".class")) {
                    var reader = new ClassReader(zin);
                    var node = new ClassNode();
                    reader.accept(node, ClassReader.SKIP_FRAMES);
                    classes.add(node);
                }
            }
        }

        return classes;
    }

    private static void writeClasses(Path path, List<ClassNode> classes) throws IOException {
        try (var zout = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var clazz : classes) {
                zout.putNextEntry(new ZipEntry(clazz.name + ".class"));

                var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS /*| ClassWriter.COMPUTE_FRAMES*/);
                clazz.accept(writer);
                zout.write(writer.toByteArray());
            }
        }
    }
}
