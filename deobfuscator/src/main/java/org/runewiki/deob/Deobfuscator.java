package org.runewiki.deob;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.classpath.JsrInliner;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.decompiler.Decompiler;
import org.runewiki.deob.bytecode.transform.*;
import org.runewiki.deob.ast.transform.*;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.printer.*;
import com.github.javaparser.printer.configuration.*;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption.*;
import com.github.javaparser.printer.configuration.*;
import com.github.javaparser.printer.configuration.Indentation.IndentType.*;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;
import com.github.javaparser.utils.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Deobfuscator {
    private static TomlParseResult toml;
    private static Map<String, Transformer> allTransformers = new HashMap<>();
    private static Map<String, AstTransformer> allAstTransformers = new HashMap<>();

    public static void registerTransformer(Transformer transformer) {
        //System.out.println("Registered transformer: " + transformer.getName());
        allTransformers.put(transformer.getName(), transformer);
        transformer.provide(toml);
    }

    public static void registerAstTransformer(AstTransformer transformer) {
        // System.out.println("Registered AST transformer: " + transformer.getName());
        allAstTransformers.put(transformer.getName(), transformer);
        transformer.provide(toml);
    }

    public static void main(String[] args) {
        try {
            toml = Toml.parse(Paths.get("deob.toml"));

            String input = toml.getString("profile.input_jar");
            String output = toml.getString("profile.output_dir");
            if (input == null || output == null) {
                System.err.println("deob.toml is invalid, see example file");
                System.exit(1);
            }

            registerTransformer(new ClassOrderTransformer());
            registerTransformer(new ExceptionTracingTransformer());
            registerTransformer(new MonitorTransformer());
            registerTransformer(new OpaquePredicateTransformer());
            registerTransformer(new OriginalNameTransformer());
            registerTransformer(new RedundantGotoTransformer());
            registerTransformer(new VisibilityTransformer());

            registerAstTransformer(new IncrementTransformer());

            System.out.println("Input: " + input);
            System.out.println("Output: " + output);

            List<ClassNode> classes = loadJar(Paths.get(input));
            System.out.println("Loaded " + classes.size() + " classes");
            System.out.println("---- Deobfuscating bytecode ----");

            TomlArray preTransformers = toml.getArray("profile.pre_transformers");
            if (preTransformers != null) {
                for (int i = 0; i < preTransformers.size(); i++) {
                    String name = preTransformers.getString(i);

                    Transformer transformer = allTransformers.get(name);
                    if (transformer != null) {
                        System.out.println("Applying " + name + " pre-transformer");
                        transformer.transform(classes);
                    } else {
                        System.err.println("Unknown transformer: " + name);
                    }
                }
            }

            Transformer remap = new RemapTransformer();
            remap.provide(toml);
            remap.transform(classes);

            TomlArray transformers = toml.getArray("profile.transformers");
            if (transformers != null) {
                for (int i = 0; i < transformers.size(); i++) {
                    String name = transformers.getString(i);

                    Transformer transformer = allTransformers.get(name);
                    if (transformer != null) {
                        System.out.println("Applying " + name + " transformer");
                        transformer.transform(classes);
                    } else {
                        System.err.println("Unknown transformer: " + name);
                    }
                }
            }

            if (Boolean.TRUE.equals(toml.getBoolean("profile.decompile"))) {
                System.out.println("---- Decompiling ----");

                Decompiler decompiler = new Decompiler(output, classes);
                decompiler.run();

                System.out.println("---- Deobfuscating AST ----");

                SourceRoot root = new SourceRoot(Paths.get(output));
                root.tryToParse();
                List<CompilationUnit> compilations = root.getCompilationUnits();

                TomlArray astTransformers = toml.getArray("profile.ast_transformers");
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
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static List<ClassNode> loadJar(Path path) throws IOException {
        List<ClassNode> classes = new ArrayList<>();

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
             ZipEntry entry;

             while (true) {
                entry = zip.getNextEntry();
                if (entry == null) {
                    break;
                }

                if (entry.getName().endsWith(".class")) {
                    ClassNode clazz = new ClassNode();
                    ClassReader reader = new ClassReader(zip);
                    reader.accept(new JsrInliner(clazz), ClassReader.SKIP_FRAMES);

                    classes.add(clazz);
                }
             }
        }

        return classes;
    }
}
