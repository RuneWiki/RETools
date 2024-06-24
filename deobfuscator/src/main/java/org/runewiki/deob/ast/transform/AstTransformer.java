package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import org.tomlj.TomlParseResult;

import java.util.List;

public class AstTransformer {
    protected TomlParseResult profile;

    public void provide(TomlParseResult profile) {
        this.profile = profile;
    }

    public String getName() {
        return this.getClass().getSimpleName().replace("Transformer", "");
    }

    public void transform(List<CompilationUnit> units) {
        preTransform();

        for (CompilationUnit unit : units) {
            transformUnit(unit);
        }

        postTransform();
    }

    public void preTransform() {
    }

    public void transformUnit(CompilationUnit unit) {
    }

    public void postTransform() {
    }
}
