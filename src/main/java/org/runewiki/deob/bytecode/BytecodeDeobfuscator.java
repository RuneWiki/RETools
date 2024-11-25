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

        registerTransformer(new RlMathTransformer());
        registerTransformer(new SortClassesLegacyTransformer());

        // openrs2
        registerTransformer(new BitShiftTransformer());
        registerTransformer(new ExceptionObfuscationTransformer());
        registerTransformer(new ExceptionTracingTransformer());
        registerTransformer(new FernflowerExceptionTransformer());
        registerTransformer(new MonitorTransformer());
        registerTransformer(new OpaquePredicateTransformer());
        registerTransformer(new VisibilityTransformer());

        // zwyz
        registerTransformer(new AnnotationRemoverTransformer()); // runelite
        registerTransformer(new DeleteInvokeDynamicTransformer()); // runelite
        registerTransformer(new AnnotateObfuscatedNamesTransformer());
        registerTransformer(new UniqueRenamerTransformer());
        registerTransformer(new StaticInstanceMethodsTransformer()); // runelite
        registerTransformer(new CalledMethodsTransformer()); // if runelite, be sure to run a second time!
        registerTransformer(new ErrorHandlersTransformer());
        registerTransformer(new ParameterChecksTransformer());
        registerTransformer(new UnreachableCodeTransformer()); // extracted out of GotoTransformer
        registerTransformer(new GotoTransformer());
        registerTransformer(new StaticMethodsTransformer());
        registerTransformer(new SortMethodsTransformer());
        registerTransformer(new StaticFieldsTransformer());
        registerTransformer(new SortFieldsNameTransformer());
        registerTransformer(new VariableSplitterTransformer());
        registerTransformer(new ExpressionSorterTransformer());
    }

    private void registerTransformer(Transformer transformer) {
        this.allTransformers.put(transformer.getName(), transformer);
        transformer.provide(this.profile);
    }

    public void run(List<ClassNode> classes) throws IOException {
        System.out.println("---- Processing bytecode ----");

        TomlArray transformers = this.profile.getArray("profile.deob.transformers");
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

        if (Boolean.TRUE.equals(profile.getBoolean("profile.remap.enable"))) {
            System.out.println("---- Remapping classes ----");
            Transformer remap = new RemapTransformer();
            remap.provide(this.profile);
            remap.transform(classes);
        }
    }
}
