package org.runewiki.deob.ast;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import org.runewiki.deob.ast.transform.*;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AstDeobfuscator {
    private final TomlParseResult profile;
    private final Map<String, AstTransformer> allAstTransformers = new HashMap<>();

    public AstDeobfuscator(TomlParseResult profile) {
        this.profile = profile;

        registerAstTransformer(new ProduceMapTransformer());

        // todo: GlTransformer
        // openrs2
        registerAstTransformer(new AddSubTransformer());
        registerAstTransformer(new BinaryExprOrderTransformer());
        registerAstTransformer(new BitMaskTransformer());
        registerAstTransformer(new CharLiteralTransformer());
        registerAstTransformer(new ComplementTransformer());
        registerAstTransformer(new EncloseTransformer());
        registerAstTransformer(new ForLoopConditionTransformer());
        registerAstTransformer(new HexLiteralTransformer());
        registerAstTransformer(new IdentityTransformer());
        registerAstTransformer(new IfElseTransformer());
        registerAstTransformer(new IncrementTransformer());
        registerAstTransformer(new NegativeLiteralTransformer());
        registerAstTransformer(new NewInstanceTransformer());
        registerAstTransformer(new NotTransformer());
        registerAstTransformer(new RedundantCastTransformer());
        registerAstTransformer(new TernaryTransformer());
        registerAstTransformer(new UnencloseTransformer());
        registerAstTransformer(new ValueOfTransformer());
    }

    private void registerAstTransformer(AstTransformer transformer) {
        // System.out.println("Registered AST transformer: " + transformer.getName());
        this.allAstTransformers.put(transformer.getName(), transformer);
        transformer.provide(this.profile);
    }

    public void run(boolean save) {
        System.out.println("---- Processing source code ----");

        var solver = new CombinedTypeSolver();

        TomlArray classpath = this.profile.getArray("profile.source.classpath");
        if (classpath != null) {
            for (int i = 0; i < classpath.size(); i++) {
                try {
                    solver.add(new JarTypeSolver(classpath.getString(i)));
                } catch (IOException ignore) {
                }
            }
        }

        solver.add(new ClassLoaderTypeSolver(ClassLoader.getPlatformClassLoader()));
        solver.add(new JavaParserTypeSolver("src/main/java"));

		var config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_6);
        config.setSymbolResolver(new JavaSymbolSolver(solver));

        SourceRoot root = new SourceRoot(Paths.get("src/main/java"), config);
        root.tryToParseParallelized();
        List<CompilationUnit> compilations = root.getCompilationUnits();

        TomlArray astTransformers = this.profile.getArray("profile.source.transformers");
        if (astTransformers != null) {
            for (int i = 0; i < astTransformers.size(); i++) {
                String name = astTransformers.getString(i);

                AstTransformer transformer = this.allAstTransformers.get(name);
                if (transformer != null) {
                    System.out.println("Applying " + name + " source transformer");
                    transformer.transform(compilations);
                } else {
                    System.err.println("Unknown AST transformer: " + name);
                }
            }
        }

        if (save) {
            root.saveAll();
        }
    }
}
