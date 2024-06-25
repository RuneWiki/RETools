package org.runewiki.deob;

import org.runewiki.decompiler.Decompiler;
import org.runewiki.deob.ast.AstDeobfuscator;
import org.runewiki.deob.bytecode.BytecodeDeobfuscator;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import zwyz.deob.JarUtil;

import java.nio.file.Paths;

public class Deobfuscator {
    public static void main(String[] args) {
        try {
            TomlParseResult profile = Toml.parse(Paths.get("deob.toml"));
            profile.errors().forEach(error -> System.err.println(error.toString()));
            if (!profile.errors().isEmpty()) {
                System.exit(1);
            }

            String inputJar = profile.getString("profile.input_jar");
            String outputJar = profile.getString("profile.output_jar");
            String outputDir = profile.getString("profile.output_dir");
            if (inputJar == null || outputJar == null || outputDir == null) {
                System.err.println("deob.toml is invalid, see example file");
                System.exit(1);
            }

            BytecodeDeobfuscator bytecode = new BytecodeDeobfuscator(profile);
            bytecode.classes = JarUtil.loadJar(Paths.get(inputJar));
            System.out.println("Loaded " + bytecode.classes.size() + " classes");

            if (Boolean.TRUE.equals(profile.getBoolean("profile.class_deob"))) {
                bytecode.run();
            }

            JarUtil.saveJar(Paths.get(outputJar), bytecode.classes);

            if (Boolean.TRUE.equals(profile.getBoolean("profile.class_decompile"))) {
                Decompiler decompiler = new Decompiler(profile, outputDir, bytecode.classes);
                decompiler.run();

                if (Boolean.TRUE.equals(profile.getBoolean("profile.java_cleanup"))) {
                    AstDeobfuscator ast = new AstDeobfuscator(profile);
                    ast.run();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
