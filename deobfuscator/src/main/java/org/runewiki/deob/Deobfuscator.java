package org.runewiki.deob;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.decompiler.Decompiler;
import org.runewiki.deob.bytecode.transform.*;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Deobfuscator {
    public static void main(String[] args) {
        try {
            TomlParseResult result = Toml.parse(Paths.get("deob.toml"));
            String input = result.getString("input");
            String output = result.getString("output");

            if (input == null || output == null) {
                throw new RuntimeException("input and output must be specified in deob.toml");
            }

            List<ClassNode> classes = Deobfuscator.loadJar(Paths.get(input));
            System.out.println("Loaded " + classes.size() + " classes");
            System.out.println("---- Deobfuscating ----");

            List<Transformer> transformers = new ArrayList<>();

            // pre-remap
            transformers.add(new OriginalNameTransformer());
            transformers.add(new ClassOrderTransformer());
            transformers.add(new RedundantGotoTransformer());

            // remap

            // post-remap
            transformers.add(new ExceptionTracingTransformer());
            transformers.add(new RedundantGotoTransformer());

            for (Transformer transformer : transformers) {
                System.out.println("Applying " + transformer.getName() + " transformer");
                transformer.transform(classes);
            }

           Decompiler decompiler = new Decompiler(output, classes);
           decompiler.run();
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
