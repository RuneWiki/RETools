package rs.lostcity.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.TypeExpr;

public class ValueOfTransformer extends AstTransformer {

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, ObjectCreationExpr.class, expr -> {
			if (expr.getType().isBoxedType()) {
				expr.replace(new MethodCallExpr(new TypeExpr(expr.getType()), "valueOf", expr.getArguments()));
			}
		});
	}
}
