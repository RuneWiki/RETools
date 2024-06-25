package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.tree.ClassNode;
import org.runewiki.asm.transform.Transformer;

import java.util.List;

/*
 * Reorder classes based on old rs2 revisions 185-303
 */
public class LegacyClassOrderTransformer extends Transformer {
    @Override
    public boolean prePass(List<ClassNode> classes) {
        // names are a-z, then ab-az, ac-az
        // this order will keep classes that are related together
        classes.sort((a, b) -> {
            if (a.name.length() == b.name.length()) {
                return a.name.charAt(a.name.length() - 1) - b.name.charAt(b.name.length() - 1);
            }

            return a.name.length() - b.name.length();
        });

        return false;
    }
}
