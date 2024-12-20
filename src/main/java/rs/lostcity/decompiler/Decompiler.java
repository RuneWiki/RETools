package rs.lostcity.decompiler;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.tomlj.TomlParseResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public class Decompiler implements IBytecodeProvider, IResultSaver {

    private final TomlParseResult profile;
    private final String output;
    private final Fernflower engine;
    private final HashMap<String, byte[]> classes = new HashMap<>();

    public Decompiler(TomlParseResult profile, String output, List<ClassNode> classNodes) {
        this.profile = profile;
        this.output = output;
        this.engine = new Fernflower(this, this, null, new PrintStreamLogger(System.out));

        for (ClassNode clazz : classNodes) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            clazz.accept(writer);
            classes.put(clazz.name, writer.toByteArray());
            engine.addSource(new File(clazz.name + ".class"));
        }

        try {
            if (Files.exists(Paths.get(output))) {
                // https://stackoverflow.com/a/42267494
                try (Stream<Path> dirStream = Files.walk(Paths.get(output))) {
                    dirStream
                        .map(Path::toFile)
                        .sorted(Comparator.reverseOrder())
                        .forEach(File::delete);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        try {
            Files.createDirectories(Paths.get(output));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void run() {
        try {
            System.out.println("---- Decompiling ----");
            this.engine.decompileContext();
        } finally {
            this.engine.clearContext();
        }
    }

    @Override
    public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
        String workingDir = System.getProperty("user.dir");
        String name = externalPath.substring(workingDir.length() + 1).replace(File.separatorChar, '/');
        return classes.get(name.replace(".class", ""));
    }

    @Override
    public void saveFolder(String path) {
    }

    @Override
    public void copyFile(String source, String path, String entryName) {
    }

    @Override
    public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
        try {
            if (qualifiedName.contains("/")) {
                String[] dirs = qualifiedName.split("/");
                String dir = "";
                for (int i = 0; i < dirs.length - 1; i++) {
                    dir += dirs[i] + "/";
                    Files.createDirectories(Paths.get(output, dir));
                }
            }
            Files.write(Paths.get(output, qualifiedName + ".java"), content.getBytes("UTF-8"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void createArchive(String path, String archiveName, Manifest manifest) {
    }

    @Override
    public void saveDirEntry(String path, String archiveName, String entryName) {
    }

    @Override
    public void copyEntry(String source, String path, String archiveName, String entryName) {
    }

    @Override
    public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
    }

    @Override
    public void closeArchive(String path, String archiveName) {
    }
}
