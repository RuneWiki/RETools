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
    public static ClassNode movedAnnotationClass = new ClassNode();

    static {
        staticsClass.version = Opcodes.V1_6; // todo: RUNELITE ? Opcodes.V1_8 : Opcodes.V1_6;
        staticsClass.access = Opcodes.ACC_PUBLIC;
        staticsClass.name = "statics";
        staticsClass.superName = "java/lang/Object";
        staticsClass.sourceFile = "statics.java";

        movedAnnotationClass.version = Opcodes.V1_6;
        movedAnnotationClass.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ANNOTATION;
        movedAnnotationClass.name = "Moved";
        movedAnnotationClass.superName = "java/lang/Object";
        movedAnnotationClass.sourceFile = "Moved.java";
    }
}
