package org.runewiki.deob;

import org.objectweb.asm.tree.ClassNode;
import org.runewiki.decompiler.Decompiler;
import org.runewiki.deob.ast.AstDeobfuscator;
import org.runewiki.deob.bytecode.BytecodeDeobfuscator;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.nio.file.Paths;
import java.util.List;

public class Deobfuscator {
    public static void main(String[] args) {
        try {
            TomlParseResult profile = Toml.parse(Paths.get("deob.toml"));
            profile.errors().forEach(error -> System.err.println(error.toString()));
            if (!profile.errors().isEmpty()) {
                System.exit(1);
            }

            String produceMap = profile.getString("profile.produce_map");
            String inputJar = profile.getString("profile.input");

            if (produceMap == null && inputJar == null) {
                System.err.println("Nothing to do!");
                System.exit(1);
            }

            if (produceMap != null) {
                System.out.println("Producing mapping file");

                AstDeobfuscator ast = new AstDeobfuscator(profile);
                ast.run(false);
            } else if (inputJar != null) {
                List<ClassNode> classes = JarUtil.readClasses(Paths.get(inputJar));
                System.out.println("Loaded " + classes.size() + " classes");

                if (Boolean.TRUE.equals(profile.getBoolean("profile.deob.enable"))) {
                    BytecodeDeobfuscator bytecode = new BytecodeDeobfuscator(profile);
                    bytecode.run(classes);
                }

                if (Boolean.TRUE.equals(profile.getBoolean("profile.source.decompile"))) {
                    Decompiler decompiler = new Decompiler(profile, "src/main/java", classes);
                    decompiler.run();

                    if (Boolean.TRUE.equals(profile.getBoolean("profile.source.cleanup"))) {
                        AstDeobfuscator ast = new AstDeobfuscator(profile);
                        ast.run(true);
                    }
                } else {
                    JarUtil.writeClasses(Paths.get(inputJar.replace(".jar", "-deob.jar")), classes);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
