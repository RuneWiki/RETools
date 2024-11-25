package rs.lostcity.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import rs.lostcity.deob.ast.util.NodeUtil;
import org.tomlj.TomlParseResult;

import java.util.List;
import java.util.function.Consumer;

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

    protected static <T extends Node> void walk(CompilationUnit unit, Class<T> type, Consumer<T> consumer) {
        NodeUtil.walk(unit, type, consumer);
    }
}
