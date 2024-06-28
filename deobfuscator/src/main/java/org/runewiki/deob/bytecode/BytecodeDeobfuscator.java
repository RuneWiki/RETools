package org.runewiki.deob.bytecode;

import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.deob.bytecode.transform.*;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BytecodeDeobfuscator {
    private final TomlParseResult profile;
    private final Map<String, Transformer> allTransformers = new HashMap<>();

    public BytecodeDeobfuscator(TomlParseResult profile) {
        this.profile = profile;

        registerTransformer(new SortClassesLegacyTransformer());
        registerTransformer(new ExceptionTracingTransformer());
        registerTransformer(new MonitorTransformer());
        registerTransformer(new OpaquePredicateTransformer());
        registerTransformer(new OriginalNameTransformer());
        registerTransformer(new RedundantGotoTransformer());
        registerTransformer(new VisibilityTransformer());
        registerTransformer(new ZwyzTransformer());
        registerTransformer(new BitShiftTransformer());
        registerTransformer(new SortFieldsNameTransformer());
        registerTransformer(new SortMethodsLineOrderTransformer());
        registerTransformer(new VariableSplitterTransformer());
        registerTransformer(new ExpressionSorterTransformer());
        registerTransformer(new FernflowerExceptionTransformer());
    }

    private void registerTransformer(Transformer transformer) {
        //System.out.println("Registered transformer: " + transformer.getName());
        this.allTransformers.put(transformer.getName(), transformer);
        transformer.provide(this.profile);
    }

    public void run(List<ClassNode> classes) throws IOException {
        System.out.println("---- Deobfuscating ----");

        TomlArray preTransformers = this.profile.getArray("profile.pre_transformers");
        if (preTransformers != null) {
            for (int i = 0; i < preTransformers.size(); i++) {
                String name = preTransformers.getString(i);

                Transformer transformer = this.allTransformers.get(name);
                if (transformer != null) {
                    System.out.println("Applying " + name + " pre-transformer");
                    transformer.transform(classes);
                } else {
                    System.err.println("Unknown transformer: " + name);
                }
            }
        }

        if (Boolean.TRUE.equals(this.profile.getBoolean("profile.class_remap"))) {
            Transformer remap = new RemapTransformer();
            remap.provide(this.profile);
            remap.transform(classes);
        }

        TomlArray transformers = this.profile.getArray("profile.transformers");
        if (transformers != null) {
            for (int i = 0; i < transformers.size(); i++) {
                String name = transformers.getString(i);

                Transformer transformer = this.allTransformers.get(name);
                if (transformer != null) {
                    System.out.println("Applying " + name + " transformer");
                    transformer.transform(classes);
                } else {
                    System.err.println("Unknown transformer: " + name);
                }
            }
        }
    }
}
