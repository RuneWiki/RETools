package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ConditionalExpr;

import static org.runewiki.deob.ast.util.ExprUtil.countNots;
import static org.runewiki.deob.ast.util.ExprUtil.not;

public class TernaryTransformer extends AstTransformer {

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, ConditionalExpr.class, expr -> {
			var condition = expr.getCondition();
			var notCondition = not(condition);
			if (countNots(notCondition) >= countNots(condition)) {
				return;
			}

			var thenExpr = expr.getThenExpr();
			var elseExpr = expr.getElseExpr();

			expr.setCondition(notCondition);

			expr.setThenExpr(elseExpr.clone());
			expr.setElseExpr(thenExpr.clone());
		});
	}
}
