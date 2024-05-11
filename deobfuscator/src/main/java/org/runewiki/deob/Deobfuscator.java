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
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Deobfuscator {
    public static void main(String[] args) {
        try {
            TomlParseResult result = Toml.parse(Paths.get("deob.toml"));
            String input = result.getString("file.input");
            String output = result.getString("file.output");

            if (input == null || output == null) {
                System.err.println("deob.toml is invalid, see example file");
                System.exit(1);
            }

            System.out.println("Input: " + input);
            System.out.println("Output: " + output);

            List<ClassNode> classes = Deobfuscator.loadJar(Paths.get(input));
            System.out.println("Loaded " + classes.size() + " classes");
            System.out.println("---- Deobfuscating ----");

            TomlArray preTransformers = result.getArray("profile.pre_transformers");
            if (preTransformers != null) {
                for (int i = 0; i < preTransformers.size(); i++) {
                    String transformer = preTransformers.getString(i);
                    System.out.println("Applying " + transformer + " transformer");

                    Class<?> clazz = Class.forName("org.runewiki.deob.bytecode.transform." + transformer + "Transformer");
                    Transformer instance = (Transformer) clazz.newInstance();
                    instance.transform(classes);
                }
            }

            // todo: remap

            TomlArray transformers = result.getArray("profile.transformers");
            if (transformers != null) {
                for (int i = 0; i < transformers.size(); i++) {
                    String transformer = transformers.getString(i);
                    System.out.println("Applying " + transformer + " transformer");

                    Class<?> clazz = Class.forName("org.runewiki.deob.bytecode.transform." + transformer + "Transformer");
                    Transformer instance = (Transformer) clazz.newInstance();
                    instance.transform(classes);
                }
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
