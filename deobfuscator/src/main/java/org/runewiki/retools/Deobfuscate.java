package org.runewiki.retools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Deobfuscate {
    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                System.out.println("Usage: java -jar deobfuscator.jar <input.jar>");
                return;
            }

            List<ClassNode> classes = Deobfuscate.loadJar(Paths.get(args[0] ));
            System.out.println("Loaded " + classes.size() + " classes");

            Decompile decompiler = new Decompile(classes);
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
                    reader.accept(node, ClassReader.SKIP_FRAMES);
                    classes.add(node);
                }
             }
        }

        return classes;
    }
}
