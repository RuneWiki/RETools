package org.runewiki.deob.bytecode.transform;

import net.runelite.asm.ClassGroup;
import net.runelite.deob.deobfuscators.arithmetic.ModArith;
import net.runelite.deob.deobfuscators.arithmetic.MultiplicationDeobfuscator;
import net.runelite.deob.deobfuscators.arithmetic.MultiplyOneDeobfuscator;
import net.runelite.deob.deobfuscators.arithmetic.MultiplyZeroDeobfuscator;
import net.runelite.deob.util.RlJarUtil;
import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.deob.JarUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

public class RlMathTransformer extends Transformer {
    private ClassGroup group = new ClassGroup();

    @Override
    public void preTransform(List<ClassNode> classes) {
        try {
            JarUtil.writeClasses(Paths.get("deob-rl-pre.jar"), classes);
            classes.clear();

            group = RlJarUtil.loadJar(new File("deob-rl-pre.jar"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        new MultiplyOneDeobfuscator(false).run(group); // changed: intentionally removing early

        ModArith mod = new ModArith();
        mod.run(group);

        int last = -1, cur;
        while ((cur = mod.runOnce()) > 0) {
            new MultiplicationDeobfuscator().run(group);

            // do not remove 1 * field so that ModArith can detect
            // the change in guessDecreasesConstants()
            new MultiplyOneDeobfuscator(true).run(group);
            new MultiplyZeroDeobfuscator().run(group);

            if (last == cur) {
                break;
            }

            last = cur;
        }

        // now that modarith is done, remove field * 1
        new MultiplyOneDeobfuscator(false).run(group);
        // mod.annotateEncryption();

        try {
            RlJarUtil.saveJar(group, new File("deob-rl-post.jar"));
            JarUtil.readClasses(Paths.get("deob-rl-post.jar"), classes);

            new File("deob-rl-pre.jar").delete();
            new File("deob-rl-post.jar").delete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
