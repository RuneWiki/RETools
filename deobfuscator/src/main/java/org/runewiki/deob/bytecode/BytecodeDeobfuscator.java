package org.runewiki.deob.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.classpath.JsrInliner;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.deob.bytecode.transform.*;
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

public class BytecodeDeobfuscator {
    private TomlParseResult profile;
    private Map<String, Transformer> allTransformers = new HashMap<>();
    public List<ClassNode> classes;

    public BytecodeDeobfuscator(TomlParseResult profile) {
        this.profile = profile;
    }

    private void registerTransformer(Transformer transformer) {
        //System.out.println("Registered transformer: " + transformer.getName());
        allTransformers.put(transformer.getName(), transformer);
        transformer.provide(profile);
    }

    public void run() throws IOException {
        registerTransformer(new ClassOrderTransformer());
        registerTransformer(new ExceptionTracingTransformer());
        registerTransformer(new MonitorTransformer());
        registerTransformer(new OpaquePredicateTransformer());
        registerTransformer(new OriginalNameTransformer());
        registerTransformer(new RedundantGotoTransformer());
        registerTransformer(new VisibilityTransformer());

        String input = profile.getString("profile.input_jar");
        String output = profile.getString("profile.output_dir");

        System.out.println("---- Deobfuscating bytecode ----");
        this.classes = loadJar(Paths.get(input));
        System.out.println("Loaded " + this.classes.size() + " classes");

        TomlArray preTransformers = profile.getArray("profile.pre_transformers");
        if (preTransformers != null) {
            for (int i = 0; i < preTransformers.size(); i++) {
                String name = preTransformers.getString(i);

                Transformer transformer = allTransformers.get(name);
                if (transformer != null) {
                    System.out.println("Applying " + name + " pre-transformer");
                    transformer.transform(this.classes);
                } else {
                    System.err.println("Unknown transformer: " + name);
                }
            }
        }

        Transformer remap = new RemapTransformer();
        remap.provide(profile);
        remap.transform(this.classes);

        TomlArray transformers = profile.getArray("profile.transformers");
        if (transformers != null) {
            for (int i = 0; i < transformers.size(); i++) {
                String name = transformers.getString(i);

                Transformer transformer = allTransformers.get(name);
                if (transformer != null) {
                    System.out.println("Applying " + name + " transformer");
                    transformer.transform(this.classes);
                } else {
                    System.err.println("Unknown transformer: " + name);
                }
            }
        }
    }

    private List<ClassNode> loadJar(Path path) throws IOException {
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
