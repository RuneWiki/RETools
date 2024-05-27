package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.runewiki.asm.AsmUtil;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.deob.bytecode.remap.SimpleObfRemapper;
import org.tomlj.TomlParseResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/*
 * Assign new names to classes, fields, and methods
 */
public class RemapTransformer extends Transformer {
    private final Map<String, String> mappings = new HashMap<>();
    private Remapper remapper;

    private String remapTxt;

    @Override
    public void provide(TomlParseResult toml) {
        super.provide(toml);

        remapTxt = toml.getString("profile.remap.remap_txt");
        if (remapTxt == null) {
            // a sane default considering the context
            remapTxt = "remap.txt";
        }
    }

    @Override
    public void preTransform(List<ClassNode> classes) {
        // generate default mappings
        int classCount = 0;
        int fieldCount = 0;
        int methodCount = 0;

        for (ClassNode clazz : classes) {
            int pkgIndex = clazz.name.lastIndexOf('/');

            if (pkgIndex != -1) {
                String pkg = clazz.name.substring(0, pkgIndex);
                String name = clazz.name.substring(pkgIndex + 1);

                if (isClassObfuscated(name)) {
                    name = pkg + "/class" + ++classCount;
                    mappings.put(clazz.name, name);
                }
            } else if (isClassObfuscated(clazz.name)) {
                mappings.put(clazz.name, "class" + ++classCount);
            }

            for (FieldNode field : clazz.fields) {
                if (isFieldObfuscated(field.name)) {
                    mappings.put(clazz.name + "." + field.name + field.desc, "field" + ++fieldCount);
                }
            }

            for (MethodNode method : clazz.methods) {
                if (method.name.startsWith("<")) {
                    continue;
                }

                if (isMethodInherited(classes, clazz, method)) {
                    // we'll catch these in a 2nd pass
                    continue;
                }

                if (isMethodObfuscated(method.name)) {
                    mappings.put(clazz.name + "." + method.name + method.desc, "method" + ++methodCount);
                }
            }
        }

        // rename inherited methods
        for (ClassNode clazz : classes) {
            for (MethodNode method : clazz.methods) {
                if (method.name.startsWith("<")) {
                    continue;
                }

                if (isMethodObfuscated(method.name) && isMethodInherited(classes, clazz, method)) {
                    ClassNode superClass = findSuperMethodOwner(classes, clazz, method);
                    if (superClass != null) {
                        String superMethod = mappings.get(superClass.name + "." + method.name + method.desc);
                        mappings.put(clazz.name + "." + method.name + method.desc, superMethod);
                    }
                }
            }
        }

        // load existing mappings and merge
        try {
            Path path = Paths.get(this.remapTxt);
            if (Files.exists(path)) {
                Files.lines(path).forEach(line -> {
                    String[] parts = line.split("=");
                    mappings.put(parts[0], parts[1]);
                });
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // save combined mappings to file
        try {
            Path path = Paths.get(this.remapTxt);
            BufferedWriter writer = Files.newBufferedWriter(path);

            // won't be ordered the way it's processed, but this is autogenerated anyway
            List<String> keys = new ArrayList<>(mappings.keySet());
            keys.sort(Comparator.naturalOrder());
            for (String key : keys) {
                writer.write(key + "=" + mappings.get(key));
                writer.newLine();
            }

            writer.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        remapper = new SimpleObfRemapper(mappings);
        for (ClassNode clazz : classes) {
            String originalClassName = clazz.name;
            clazz.name = remapper.map(clazz.name);
            clazz.superName = remapper.map(clazz.superName);
            clazz.interfaces.replaceAll(remapper::map);
            clazz.fields.forEach(f -> {
                f.name = remapper.mapFieldName(originalClassName, f.name, f.desc);
                f.desc = remapper.mapDesc(f.desc);
            });
            clazz.methods.forEach(m -> {
                m.name = remapper.mapMethodName(originalClassName, m.name, m.desc);
                m.desc = remapper.mapMethodDesc(m.desc);

                if (AsmUtil.hasCode(m)) {
                    remapInstructions(remapper, m.instructions);

                    for (TryCatchBlockNode tryCatch : m.tryCatchBlocks) {
                        tryCatch.type = remapper.map(tryCatch.type);
                    }
                }
            });
        }
    }

    private void remapInstructions(Remapper remapper, InsnList instructions) {
        for (AbstractInsnNode insn : instructions) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                fieldInsn.name = remapper.mapFieldName(fieldInsn.owner, fieldInsn.name, fieldInsn.desc);
                fieldInsn.desc = remapper.mapDesc(fieldInsn.desc);
                fieldInsn.owner = remapper.map(fieldInsn.owner);
            } else if (insn instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                methodInsn.name = remapper.mapMethodName(methodInsn.owner, methodInsn.name, methodInsn.desc);
                methodInsn.desc = remapper.mapMethodDesc(methodInsn.desc);
                methodInsn.owner = remapper.map(methodInsn.owner);
            } else if (insn instanceof TypeInsnNode) {
                TypeInsnNode typeInsn = (TypeInsnNode) insn;
                typeInsn.desc = remapper.map(typeInsn.desc);
            } else if (insn instanceof LdcInsnNode) {
                LdcInsnNode ldcInsn = (LdcInsnNode) insn;
                if (ldcInsn.cst instanceof Type) {
                    ldcInsn.cst = Type.getType(remapper.map(((Type) ldcInsn.cst).getDescriptor()));
                }
            } else if (insn instanceof MultiANewArrayInsnNode) {
                MultiANewArrayInsnNode multiANewArrayInsn = (MultiANewArrayInsnNode) insn;
                multiANewArrayInsn.desc = remapper.mapType(multiANewArrayInsn.desc);
            } else if (insn instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode invokeDynamicInsn = (InvokeDynamicInsnNode) insn;
                invokeDynamicInsn.desc = remapper.mapMethodDesc(invokeDynamicInsn.desc);
            }
        }
    }

    private boolean isClassObfuscated(String name) {
        return name.length() < 3 || name.equals(name.toUpperCase());
    }

    private boolean isFieldObfuscated(String name) {
        return name.length() < 3;
    }

    private boolean isMethodObfuscated(String name) {
        return name.length() < 3;
    }

    private boolean isMethodInherited(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        for (ClassNode otherClazz : classes) {
            if (clazz.superName.equals(otherClazz.name)) {
                for (MethodNode otherMethod : otherClazz.methods) {
                    if (otherMethod.name.equals(method.name) && otherMethod.desc.equals(method.desc)) {
                        return true;
                    }
                }

                return isMethodInherited(classes, otherClazz, method);
            }
        }

        return false;
    }

    private ClassNode findSuperMethodOwner(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        for (ClassNode otherClazz : classes) {
            if (clazz.superName.equals(otherClazz.name)) {
                for (MethodNode otherMethod : otherClazz.methods) {
                    if (otherMethod.name.equals(method.name) && otherMethod.desc.equals(method.desc)) {
                        return otherClazz;
                    }
                }

                return findSuperMethodOwner(classes, otherClazz, method);
            }
        }

        return null;
    }
}
