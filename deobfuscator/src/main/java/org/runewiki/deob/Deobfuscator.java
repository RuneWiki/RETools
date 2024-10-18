package org.runewiki.deob;

import net.runelite.asm.ClassGroup;
import net.runelite.deob.Deob;
import net.runelite.deob.util.RlJarUtil;
import org.objectweb.asm.tree.ClassNode;
import org.runewiki.decompiler.Decompiler;
import org.runewiki.deob.ast.AstDeobfuscator;
import org.runewiki.deob.bytecode.BytecodeDeobfuscator;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import zwyz.deob.JarUtil;
import zwyz.deob.ZwyzDeobStep1;
import zwyz.deob.ZwyzDeobStep2;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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

            String defaultPackage = profile.getString("profile.default_package");

            List<ClassNode> classes;
            if (Boolean.TRUE.equals(profile.getBoolean("profile.zwyz.full"))) {
                boolean runelite = Boolean.TRUE.equals(profile.getBoolean("profile.zwyz.runelite"));
                boolean unreliableClassOrder = Boolean.TRUE.equals(profile.getBoolean("profile.zwyz.unreliable_class_order"));
                boolean complexParameterChecks = Boolean.TRUE.equals(profile.getBoolean("profile.zwyz.complex_parameter_checks"));
                boolean showLineNumbers = Boolean.TRUE.equals(profile.getBoolean("profile.zwyz.show_line_numbers"));
                boolean trackMoved = Boolean.TRUE.equals(profile.getBoolean("profile.zwyz.track_moved"));

                System.out.println("zwyz: step 1");
                ZwyzDeobStep1.run(Paths.get(inputJar), Paths.get(outputJar + "-deob.jar"), runelite, unreliableClassOrder, complexParameterChecks, showLineNumbers, trackMoved);

                if (Boolean.TRUE.equals(profile.getBoolean("profile.zwyz.math"))) {
                    System.out.println("zwyz: rl-math");
                    ClassGroup group = RlJarUtil.loadJar(new File(outputJar + "-deob.jar"));
                    Deob.runMath(group);
                    RlJarUtil.saveJar(group, new File(outputJar + "-nomultipliers.jar"));

                    System.out.println("zwyz: step 2");
                    ZwyzDeobStep2.run(Paths.get("mapping.txt"), Paths.get(outputJar + "-nomultipliers.jar"), Paths.get(outputJar + "-named.jar"), defaultPackage);
                } else {
                    System.out.println("zwyz: step 2");
                    ZwyzDeobStep2.run(Paths.get("mapping.txt"), Paths.get(outputJar + "-deob.jar"), Paths.get(outputJar + "-named.jar"), defaultPackage);
                }

                System.out.println("zwyz: done!");
                classes = JarUtil.readClasses(Paths.get(outputJar + "-named.jar"));
            } else {
                classes = JarUtil.readClasses(Paths.get(inputJar));
                System.out.println("Loaded " + classes.size() + " classes");

                if (Boolean.TRUE.equals(profile.getBoolean("profile.class_deob"))) {
                    BytecodeDeobfuscator bytecode = new BytecodeDeobfuscator(profile);
                    bytecode.run(classes);
                }
            }

            if (Boolean.TRUE.equals(profile.getBoolean("profile.class_decompile"))) {
                Decompiler decompiler = new Decompiler(profile, outputDir, classes);
                decompiler.run();

                if (Boolean.TRUE.equals(profile.getBoolean("profile.java_cleanup"))) {
                    AstDeobfuscator ast = new AstDeobfuscator(profile);
                    ast.run();
                }
            } else {
                JarUtil.writeClasses(Paths.get(outputJar), classes);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
