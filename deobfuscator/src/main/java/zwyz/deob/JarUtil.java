package zwyz.deob;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class JarUtil {
    public static List<ClassNode> readClasses(Path path) throws IOException {
        var classes = new ArrayList<ClassNode>();

        try (var zin = new ZipInputStream(Files.newInputStream(path))) {
            while (true) {
                var entry = zin.getNextEntry();

                if (entry == null) {
                    break;
                }

                var string = entry.getName();

                if (string.endsWith(".class")) {
                    var reader = new ClassReader(zin);
                    var node = new ClassNode();
                    reader.accept(node, ClassReader.SKIP_FRAMES);
                    classes.add(node);
                }
            }
        }

        return classes;
    }

    public static void readClasses(Path path, List<ClassNode> classes) throws IOException {
        classes.clear();

        try (var zin = new ZipInputStream(Files.newInputStream(path))) {
            while (true) {
                var entry = zin.getNextEntry();

                if (entry == null) {
                    break;
                }

                var string = entry.getName();

                if (string.endsWith(".class")) {
                    var reader = new ClassReader(zin);
                    var node = new ClassNode();
                    reader.accept(node, ClassReader.SKIP_FRAMES);
                    classes.add(node);
                }
            }
        }
    }

    public static void writeClasses(Path path, List<ClassNode> classes) throws IOException {
        try (var zout = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var clazz : classes) {
                zout.putNextEntry(new ZipEntry(clazz.name + ".class"));

                var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS /*| ClassWriter.COMPUTE_FRAMES*/);
                clazz.accept(writer);
                zout.write(writer.toByteArray());
            }
        }
    }
}
