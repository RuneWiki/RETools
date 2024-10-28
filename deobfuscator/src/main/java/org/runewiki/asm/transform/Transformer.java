package org.runewiki.asm.transform;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.runewiki.asm.InsnNodeUtil;
import org.tomlj.TomlParseResult;

import java.util.List;

public class Transformer {
    protected TomlParseResult profile;

    public void provide(TomlParseResult profile) {
        this.profile = profile;
    }

    public String getName() {
        return this.getClass().getSimpleName().replace("Transformer", "");
    }

    public void transform(List<ClassNode> classes) {
        this.preTransform(classes);

        boolean changed;
        do {
            changed = this.prePass(classes);

            for (ClassNode clazz : classes) {
                changed |= this.transformClass(classes, clazz);

                for (FieldNode field : clazz.fields) {
                    changed |= this.transformField(classes, clazz, field);
                }

                for (MethodNode method : clazz.methods) {
                    changed |= this.transformMethod(classes, clazz, method);

                    if (InsnNodeUtil.hasCode(method)) {
                        changed |= this.transformCode(classes, clazz, method);
                    }

                    changed |= this.postTransformMethod(classes, clazz, method);
                }

                changed |= this.postTransformClass(classes, clazz);
            }

            changed |= this.postPass(classes);
        } while (changed);

        this.postTransform(classes);
    }

    public void preTransform(List<ClassNode> classes) {
    }

    public boolean prePass(List<ClassNode> classes) {
        return false;
    }

    public boolean transformClass(List<ClassNode> classes, ClassNode clazz) {
        return false;
    }

    public boolean transformField(List<ClassNode> classes, ClassNode clazz, FieldNode field) {
        return false;
    }

    public boolean transformMethod(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        return false;
    }

    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        return false;
    }

    public boolean postTransformMethod(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        return false;
    }

    public boolean postTransformClass(List<ClassNode> classes, ClassNode clazz) {
        return false;
    }

    public boolean postPass(List<ClassNode> classes) {
        return false;
    }

    public void postTransform(List<ClassNode> classes) {
    }

    public void finalTransform(List<ClassNode> classes) {
    }
}
