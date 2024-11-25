package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.EnclosedExpr;

public class UnencloseTransformer extends AstTransformer {

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, EnclosedExpr.class, expr -> {
			expr.replace(expr.getInner().clone());
		});
	}
}
