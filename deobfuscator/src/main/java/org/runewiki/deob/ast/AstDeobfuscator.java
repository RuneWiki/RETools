package org.runewiki.deob.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.SourceRoot;
import org.runewiki.deob.ast.transform.AstTransformer;
import org.runewiki.deob.ast.transform.IncrementTransformer;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AstDeobfuscator {
    private TomlParseResult profile;
    private Map<String, AstTransformer> allAstTransformers = new HashMap<>();

    public AstDeobfuscator(TomlParseResult profile) {
        this.profile = profile;
    }

    private void registerAstTransformer(AstTransformer transformer) {
        // System.out.println("Registered AST transformer: " + transformer.getName());
        allAstTransformers.put(transformer.getName(), transformer);
        transformer.provide(this.profile);
    }

    public void run() {
        registerAstTransformer(new IncrementTransformer());

        String output = profile.getString("profile.output_dir");

        System.out.println("---- Deobfuscating AST ----");

        SourceRoot root = new SourceRoot(Paths.get(output));

        root.tryToParseParallelized();
        List<CompilationUnit> compilations = root.getCompilationUnits();

        TomlArray astTransformers = profile.getArray("profile.ast_transformers");
        if (astTransformers != null) {
            for (int i = 0; i < astTransformers.size(); i++) {
                String name = astTransformers.getString(i);

                AstTransformer transformer = allAstTransformers.get(name);
                if (transformer != null) {
                    System.out.println("Applying " + name + " AST transformer");
                    transformer.transform(compilations);
                } else {
                    System.err.println("Unknown AST transformer: " + name);
                }
            }
        }

        root.saveAll();
    }
}
