package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.transform.Transformer;
import org.tomlj.TomlParseResult;
import zwyz.deob.CalledMethods;
import zwyz.deob.ErrorHandlers;
import zwyz.deob.GotoDeobfuscator;
import zwyz.deob.ParameterChecks;

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
        GotoDeobfuscator.run(classes, this.removeUnreachable);
    }
}
