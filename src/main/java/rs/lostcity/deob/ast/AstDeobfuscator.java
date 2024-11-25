package rs.lostcity.deob.ast;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.*;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;
import com.github.javaparser.utils.SourceRoot;
import rs.lostcity.deob.ast.transform.*;
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
                } catch (IOException ex) {
                    System.err.println("Failed to apply source classpath for: " + classpath.getString(i));
                }
            }
        }

        solver.add(new ReflectionTypeSolver(false));
        solver.add(new ClassLoaderTypeSolver(ClassLoader.getPlatformClassLoader()));
        solver.add(new JavaParserTypeSolver("src/main/java"));

		var config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        config.setSymbolResolver(new JavaSymbolSolver(solver));

        if (Boolean.TRUE.equals(profile.getBoolean("profile.source.preserve_format"))) {
            config.setLexicalPreservationEnabled(true);
        }

        SourceRoot root = new SourceRoot(Paths.get("src/main/java"), config);

        if (Boolean.TRUE.equals(profile.getBoolean("profile.source.preserve_format"))) {
            root.setPrinter(LexicalPreservingPrinter::print);
        } else {
            DefaultPrinterConfiguration prettyConfig = new DefaultPrinterConfiguration();
            prettyConfig.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.INDENTATION, new Indentation(Indentation.IndentType.TABS_WITH_SPACE_ALIGN, 1)));
            prettyConfig.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.INDENT_CASE_IN_SWITCH, false));
            prettyConfig.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.ORDER_IMPORTS, true));

            DefaultPrettyPrinter pretty = new DefaultPrettyPrinter(prettyConfig);
            root.setPrinter(pretty::print);
        }

        var results = root.tryToParseParallelized();
        for (var result : results) {
            if (!result.isSuccessful()) {
                for (var problem : result.getProblems()) {
                    System.err.println(problem.toString());
                }
            }
        }

        TomlArray astTransformers = this.profile.getArray("profile.source.transformers");
        if (astTransformers != null) {
            List<CompilationUnit> units = root.getCompilationUnits();

            for (int i = 0; i < astTransformers.size(); i++) {
                String name = astTransformers.getString(i);

                AstTransformer transformer = this.allAstTransformers.get(name);
                if (transformer != null) {
                    System.out.println("Applying " + name + " source transformer");
                    transformer.transform(units);
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
