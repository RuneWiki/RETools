package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

class ZwyzLegacyLogic {
    public static Set<String> calledMethods = new LinkedHashSet<>();
    public static Set<String> obfuscatedMethods = new HashSet<>();
    public static Set<String> unobfuscatedMethods = new HashSet<>();

    public static ClassNode staticsClass = new ClassNode();

    static {
        staticsClass.version = Opcodes.V1_6; // todo: RUNELITE ? Opcodes.V1_8 : Opcodes.V1_6;
        staticsClass.access = Opcodes.ACC_PUBLIC;
        staticsClass.name = "deob/statics";
        staticsClass.superName = "java/lang/Object";
        staticsClass.sourceFile = "statics.java";
    }
}
