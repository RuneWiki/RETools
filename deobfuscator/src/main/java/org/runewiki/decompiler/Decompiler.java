package org.runewiki.decompiler;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.jar.Manifest;

public class Decompiler implements IBytecodeProvider, IResultSaver {

    private final Fernflower engine;
    private HashMap<String, byte[]> classes = new HashMap<>();

    public Decompiler(List<ClassNode> classNodes) {
        this.engine = new Fernflower(this, this, null, new PrintStreamLogger(System.out));

        for (ClassNode node : classNodes) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            classes.put(node.name, writer.toByteArray());
            engine.addSource(new File(node.name + ".class"));
        }
    }

    public void run() {
        System.out.println("---- Decompiling ----");

        try {
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
            Files.createDirectories(Paths.get("dump", "out"));
            if (qualifiedName.contains("/")) {
                String[] dirs = qualifiedName.split("/");
                String dir = "";
                for (int i = 0; i < dirs.length - 1; i++) {
                    dir += dirs[i] + "/";
                    Files.createDirectories(Paths.get("dump", "out", dir));
                }
            }
            Files.write(Paths.get("dump", "out", qualifiedName + ".java"), content.getBytes());
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
