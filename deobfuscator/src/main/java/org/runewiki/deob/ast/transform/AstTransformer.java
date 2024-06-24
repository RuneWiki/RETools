package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import org.tomlj.TomlParseResult;
import java.util.*;

public class AstTransformer {
    protected TomlParseResult toml;

    public void provide(TomlParseResult toml) {
        this.toml = toml;
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
