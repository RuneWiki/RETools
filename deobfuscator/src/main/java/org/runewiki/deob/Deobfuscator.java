package org.runewiki.deob;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.decompiler.Decompiler;
import org.runewiki.deob.bytecode.transform.*;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

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

    public static void registerTransformer(Transformer transformer) {
        //System.out.println("Registered transformer: " + transformer.getName());
        allTransformers.put(transformer.getName(), transformer);
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

            System.out.println("Input: " + input);
            System.out.println("Output: " + output);

            List<ClassNode> classes = loadJar(Paths.get(input));
            System.out.println("Loaded " + classes.size() + " classes");
            System.out.println("---- Deobfuscating ----");

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
                Decompiler decompiler = new Decompiler(output, classes);
                decompiler.run();
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
                    ClassReader reader = new ClassReader(zip);
                    ClassNode node = new ClassNode();
                    reader.accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                    classes.add(node);
                }
             }
        }

        return classes;
    }
}
