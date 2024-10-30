package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ProduceMapTransformer extends AstTransformer {
    private String result = "";

    @Override
    public void preTransform() {
        File file = new File("remap.txt");
        if (file.exists()) {
            file.delete();
        }

        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void postTransform() {
        File file = new File("remap.txt");
        try {
            Files.write(file.toPath(), result.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void transformUnit(CompilationUnit unit) {
        AtomicReference<String> pkgName = new AtomicReference<>();

        walk(unit, PackageDeclaration.class, pkg -> {
            pkgName.set(pkg.getNameAsString());
        });

        walk(unit, AnnotationDeclaration.class, annotation -> {
            String originalName = getOriginalName(annotation.getAnnotations());
            if (originalName == null) {
                return;
            }

            if (pkgName.get() != null) {
                result += originalName + "=" + pkgName + "." + annotation.getNameAsString() + "\n";
            } else {
                result += originalName + "=" + annotation.getNameAsString() + "\n";
            }
        });

        // todo: detect inner classes
        walk(unit, ClassOrInterfaceDeclaration.class, clazz -> {
            /*if (clazz.getNameAsString().startsWith("class")) {
                return;
            }*/

            String foundClass = getOriginalName(clazz.getAnnotations());
            if (foundClass != null) {
                String newClass;
                if (pkgName.get() != null) {
                    newClass = pkgName + "." + clazz.getNameAsString();
                } else {
                    newClass = clazz.getNameAsString();
                }

                result += foundClass + "=" + newClass + "\n";
            } /* else {
                foundClass = clazz.getNameAsString();

                if (pkgName.get() != null) {
                    result += foundClass + "=" + pkgName + "." + clazz.getNameAsString() + "\n";
                }
            } */

            final String originalClass = foundClass;

            clazz.getFields().forEach(field -> {
                String fieldName = field.getVariables().get(0).getNameAsString();
                /*if (fieldName.startsWith("field")) {
                    return;
                }*/

                String originalName = getOriginalName(field.getAnnotations());
                if (originalName == null) {
                    return;
                }

                if (field.isStatic()) {
                    result += originalName + "=" + originalClass + "," + fieldName + "\n";
                } else {
                    result += originalName + "=" + fieldName + "\n";
                }
            });

            clazz.getMethods().forEach(method -> {
                String memberName = method.getNameAsString();
                /*if (memberName.startsWith("method")) {
                    return;
                }*/

                String originalName = getOriginalName(method.getAnnotations());
                if (originalName == null) {
                    return;
                }

                if (method.isStatic()) {
                    result += originalName + "=" + originalClass + "," + memberName + "\n";
                } else {
                    result += originalName + "=" + memberName + "\n";
                }
            });
        });
    }

    public String getOriginalName(List<AnnotationExpr> annotations) {
        if (annotations.isEmpty()) {
            return null;
        }

        String originalName = null;

        // see if we recognize the annotation format
        for (AnnotationExpr expr : annotations) {
            if (originalName != null) {
                break;
            }

            if (
                (expr.getNameAsString().equals("ObfuscatedName") || expr.getNameAsString().equals("OriginalClass")) &&
                expr.isSingleMemberAnnotationExpr()
            ) {
                originalName = ((SingleMemberAnnotationExpr) expr).getMemberValue().toString().replace("\"", "");
            } else if (expr.getNameAsString().equals("OriginalMember") && expr.isNormalAnnotationExpr()) {
                String owner = ((NormalAnnotationExpr) expr).getPairs().get(0).getValue().toString().replace("\"", "");
                String name = ((NormalAnnotationExpr) expr).getPairs().get(1).getValue().toString().replace("\"", "");
                String descriptor = ((NormalAnnotationExpr) expr).getPairs().get(2).getValue().toString().replace("\"", "");

                originalName = owner + "." + name + descriptor;
            }
        }

        if (originalName == null || originalName.isEmpty() || originalName.contains("<")) {
            return null;
        }

        // strip out the library information generated by openrs2's deob tool
        if (originalName.contains("!")) {
            originalName = originalName.replaceAll("L\\w+!", "L");
            originalName = originalName.replaceAll("\\w+!", "");
        }

        return originalName;
    }
}
