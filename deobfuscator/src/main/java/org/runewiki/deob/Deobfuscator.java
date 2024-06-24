package org.runewiki.deob;

import org.runewiki.decompiler.Decompiler;
import org.runewiki.deob.ast.AstDeobfuscator;
import org.runewiki.deob.bytecode.BytecodeDeobfuscator;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.nio.file.Paths;

public class Deobfuscator {
    public static void main(String[] args) {
        try {
            TomlParseResult profile = Toml.parse(Paths.get("deob.toml"));

            String input = profile.getString("profile.input_jar");
            String output = profile.getString("profile.output_dir");
            if (input == null || output == null) {
                System.err.println("deob.toml is invalid, see example file");
                System.exit(1);
            }

            System.out.println("Input: " + input);
            System.out.println("Output: " + output);

            BytecodeDeobfuscator bytecodeDeobfuscator = new BytecodeDeobfuscator(profile);
            bytecodeDeobfuscator.run();

            if (Boolean.TRUE.equals(profile.getBoolean("profile.decompile"))) {
                Decompiler decompiler = new Decompiler(profile, output, bytecodeDeobfuscator.classes);
                decompiler.run();

                // deobfuscate AST (relies on decompiled java output)
                AstDeobfuscator astDeobfuscator = new AstDeobfuscator(profile);
                astDeobfuscator.run();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
