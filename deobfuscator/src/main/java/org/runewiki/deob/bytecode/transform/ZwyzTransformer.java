package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.transform.Transformer;
import org.tomlj.TomlParseResult;
import zwyz.deob.*;

import java.util.HashSet;
import java.util.List;

public class ZwyzTransformer extends Transformer {
    private boolean removeUnreachable = true;

    @Override
    public void provide(TomlParseResult profile) {
        super.provide(profile);

        this.removeUnreachable = Boolean.TRUE.equals(this.profile.getBoolean("profile.remove_unreachable"));
    }

    @Override
    public void transform(List<ClassNode> classes) {
        var calledMethods = CalledMethods.run(classes);
        var obfuscatedMethods = new HashSet<String>();
        var unobfuscatedMethods = new HashSet<String>();
        ErrorHandlers.run(classes, calledMethods, obfuscatedMethods, unobfuscatedMethods);
        ParameterChecks.run(classes, obfuscatedMethods, unobfuscatedMethods);
        GotoDeobfuscator.run(classes, this.removeUnreachable); // Undo goto obfuscation, so that matching statics can work properly

//        var staticsClass = new ClassNode();
//        staticsClass.version = Opcodes.V1_6;
//        staticsClass.access = Opcodes.ACC_PUBLIC;
//        staticsClass.name = "statics";
//        staticsClass.superName = "java/lang/Object";
//        staticsClass.sourceFile = "statics.java";
//        classes.add(staticsClass);

        // todo: move extracting statics into its own transformer
        // StaticMethods.run(classes, calledMethods, obfuscatedMethods, staticsClass);
        SortMethods.run(classes);
        // StaticFields.run(classes, staticsClass);
        SortFieldsName.run(classes);
        VariableSplitter.run(classes);
        ExpressionSorter.run(classes);
    }
}
