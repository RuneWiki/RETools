package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.openrs2.deob.annotation.OriginalClass;
import org.runewiki.asm.transform.Transformer;
import org.tomlj.TomlParseResult;

import java.util.List;

/*
 * Add OpenRS2's OriginalClass and OriginalMember annotations to classes, fields, and methods
 */
public class OriginalNameTransformer extends Transformer {
    private String libraryAnnotation;

    @Override
    public void provide(TomlParseResult toml) {
        super.provide(toml);

        libraryAnnotation = toml.getString("profile.original_name.library_annotation");
        if (libraryAnnotation == null) {
            // a sane default considering the context
            libraryAnnotation = "client";
        }
    }

    @Override
    public void postTransform(List<ClassNode> classes) {
        try {
            {
                ClassReader reader = new ClassReader("org.openrs2.deob.annotation.OriginalClass");
                ClassNode clazz = new ClassNode();
                reader.accept(clazz, 0);
                classes.add(clazz);
            }

            {
                ClassReader reader = new ClassReader("org.openrs2.deob.annotation.OriginalMember");
                ClassNode clazz = new ClassNode();
                reader.accept(clazz, 0);
                classes.add(clazz);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public boolean preTransformClass(List<ClassNode> classes, ClassNode clazz) {
        AnnotationVisitor annotation = clazz.visitAnnotation("Lorg.openrs2.deob.annotation.OriginalClass;", false);
        annotation.visit("value", libraryAnnotation + "!" + clazz.name);
        annotation.visitEnd();
        return false;
    }

    @Override
    public boolean transformField(List<ClassNode> classes, ClassNode clazz, FieldNode field) {
        AnnotationVisitor annotation = field.visitAnnotation("Lorg.openrs2.deob.annotation.OriginalMember;", false);
        annotation.visit("owner", libraryAnnotation + "!" + clazz.name);
        annotation.visit("name", field.name);
        annotation.visit("descriptor", field.desc);
        annotation.visitEnd();
        return false;
    }

    @Override
    public boolean preTransformMethod(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        if (method.name.equals("<clinit>")) {
            return false;
        }

        AnnotationVisitor annotation = method.visitAnnotation("Lorg.openrs2.deob.annotation.OriginalMember;", false);
        annotation.visit("owner", libraryAnnotation + "!" + clazz.name);
        annotation.visit("name", method.name);
        annotation.visit("descriptor", method.desc);

        AbstractInsnNode insn = method.instructions.getFirst();
        while (insn != null) {
            if (insn instanceof LineNumberNode) {
                break;
            }

            insn = insn.getNext();
        }

        if (insn != null) {
            annotation.visit("line", ((LineNumberNode) insn).line);
        }

        annotation.visitEnd();
        return false;
    }
}
