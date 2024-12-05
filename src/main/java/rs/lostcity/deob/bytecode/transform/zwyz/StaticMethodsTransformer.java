package rs.lostcity.deob.bytecode.transform.zwyz;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import rs.lostcity.asm.transform.Transformer;
import rs.lostcity.deob.bytecode.StringConvertingMethodVisitor;

import java.util.*;

public class StaticMethodsTransformer extends Transformer {
    @Override
    public void transform(List<ClassNode> classes) {
        classes.add(ZwyzLegacyLogic.staticsClass);

        // Compute method hashes
        var methodOwners = new HashMap<String, String>();
        var methodsByHash = new HashMap<Integer, List<String>>();

        for (var clazz : classes) {
            for (var method : clazz.methods) {
                if (method.name.startsWith("method") && (!ZwyzLegacyLogic.calledMethods.contains(method.name) || ZwyzLegacyLogic.obfuscatedMethods.contains(method.name)) && (method.access & Opcodes.ACC_STATIC) != 0 && (method.access & Opcodes.ACC_ABSTRACT) == 0) {
                    var hash = computeHash(method);
                    methodOwners.put(method.name, clazz.name);
                    methodsByHash.computeIfAbsent(hash, k -> new ArrayList<>()).add(method.name);
                }
            }
        }

        var realOwners = new HashMap<String, String>();

        for (var entry : methodsByHash.entrySet()) {
            var group = entry.getValue();

            if (group.size() < 2) {
                continue;
            }

            var real = new ArrayList<String>();
            var owners = new HashSet<String>();

            for (var method : group) {
                if (ZwyzLegacyLogic.calledMethods.contains(method)) {
                    real.add(method);
                } else {
                    owners.add(methodOwners.get(method));
                }
            }

            if (owners.size() != 1) {
                continue;
            }

            // note: we don't need to check that real.size() == 1, if it's > 1, there is
            // ambiguity in the match, but it doesn't matter as long as all the possible
            // matches are in the same class

            for (var r : real) {
                var realOwner = owners.stream().findFirst().orElse(null);
                realOwners.put(r, realOwner);
            }
        }

        for (var clazz : classes) {
            for (var method : clazz.methods) {
                if (method.name.startsWith("method") && (method.access & Opcodes.ACC_STATIC) != 0 && ZwyzLegacyLogic.obfuscatedMethods.contains(method.name)) {
                    realOwners.putIfAbsent(method.name, "Statics");
                }
            }
        }

        // Delete uncalled methods, we don't need them anymore
        for (var clazz : classes) {
            clazz.methods.removeIf(m -> !ZwyzLegacyLogic.calledMethods.contains(m.name));
        }

        // Move static methods to the real owners
        var classesByName = new HashMap<String, ClassNode>();

        for (var clazz : classes) {
            classesByName.put(clazz.name, clazz);
        }

        for (var clazz : classes) {
            for (var method : new ArrayList<>(clazz.methods)) {
                var realOwner = realOwners.get(method.name);

                if (realOwner != null && !Objects.equals(realOwner, clazz.name)) {
                    clazz.methods.remove(method);
                    classesByName.get(realOwner).methods.add(method);
                    method.access = (method.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                }

                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode mi && realOwners.containsKey(mi.name)) {
                        mi.owner = realOwners.get(mi.name);
                    }
                }
            }
        }
    }

    private static int computeHash(MethodNode method) {
        var visitor = new StringConvertingMethodVisitor() {
            @Override
            public void visitIntInsn(int opcode, int operand) {
                if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                    result.add("(constant int)");
                } else {
                    super.visitInsn(opcode);
                }
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Integer) {
                    result.add("(constant int)");
                } else {
                    super.visitLdcInsn(value);
                }
            }
        };

        method.instructions.accept(visitor);
        return visitor.getResult().hashCode() * 31 + method.desc.hashCode();
    }
}
