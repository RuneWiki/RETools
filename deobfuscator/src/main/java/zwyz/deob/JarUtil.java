package zwyz.deob;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.classpath.JsrInliner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class JarUtil {
    public static void loadJar(Path path, List<ClassNode> classes) throws IOException {
        try (var zin = new ZipInputStream(Files.newInputStream(path))) {
            while (true) {
                var entry = zin.getNextEntry();
                if (entry == null) {
                    break;
                }

                if (entry.getName().endsWith(".class")) {
                    var reader = new ClassReader(zin);
                    var clazz = new ClassNode();
                    reader.accept(new JsrInliner(clazz), ClassReader.SKIP_FRAMES);
                    classes.add(clazz);
                }
            }
        }
    }

    public static void saveJar(Path path, List<ClassNode> classes) throws IOException {
        try (var zout = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var clazz : classes) {
                zout.putNextEntry(new ZipEntry(clazz.name + ".class"));

                var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                clazz.accept(writer);
                zout.write(writer.toByteArray());
            }
        }
    }
}
