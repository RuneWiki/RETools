package rs.lostcity.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.UnaryExpr;

import static rs.lostcity.deob.ast.util.ExprUtil.isIntOrLongLiteral;
import static rs.lostcity.deob.ast.util.ExprUtil.negate;

public class NegativeLiteralTransformer extends AstTransformer {

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, UnaryExpr.class, expr -> {
			var operand = expr.getExpression();
			if (!isIntOrLongLiteral(operand)) {
				return;
			}
			switch (expr.getOperator()) {
				case PLUS -> expr.replace(operand.clone());
				case MINUS -> expr.replace(negate(operand));
			}
		});
	}
}
