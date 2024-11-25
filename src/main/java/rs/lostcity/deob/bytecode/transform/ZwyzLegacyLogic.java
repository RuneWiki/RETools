package rs.lostcity.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.*;

public class ZwyzLegacyLogic {
    // todo: move list to profile
    public static final List<String> EXTERNAL_LIBRARIES = List.of(
            "com/jagex/oldscape/pub",
            "org/bouncycastle/",
            "org/json/"
    );

    public static Set<String> calledMethods = new LinkedHashSet<>();
    public static Set<String> obfuscatedMethods = new HashSet<>();
    public static Set<String> unobfuscatedMethods = new HashSet<>();

    public static ClassNode staticsClass = new ClassNode();

    static {
        staticsClass.version = Opcodes.V1_6; // todo: RUNELITE ? Opcodes.V1_8 : Opcodes.V1_6;
        staticsClass.access = Opcodes.ACC_PUBLIC;
        staticsClass.name = "Statics";
        staticsClass.superName = "java/lang/Object";
        staticsClass.sourceFile = "Statics.java";
    }
}
