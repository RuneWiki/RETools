package rs.lostcity.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import rs.lostcity.deob.ast.util.NodeUtil;

public class NewInstanceTransformer extends AstTransformer {

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, MethodCallExpr.class, expr -> {
			if (!expr.getNameAsString().equals("newInstance")) {
				return;
			}
			expr.getScope().ifPresent(scope -> {
				if (NodeUtil.isClass(scope.calculateResolvedType())) {
					expr.setScope(new MethodCallExpr(scope.clone(), "getDeclaredConstructor"));
				}
			});
		});
	}
}
