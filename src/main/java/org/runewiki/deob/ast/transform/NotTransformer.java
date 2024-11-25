package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;

import static com.github.javaparser.ast.expr.BinaryExpr.Operator.EQUALS;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.NOT_EQUALS;
import static com.github.javaparser.ast.expr.UnaryExpr.Operator.LOGICAL_COMPLEMENT;
import static org.runewiki.deob.ast.util.ExprUtil.not;

public class NotTransformer extends AstTransformer {

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, BinaryExpr.class, expr -> {
			var op = flip(expr.getOperator());
			if (op == null) return;

			var left = expr.getLeft();
			var right = expr.getRight();

			var bothLiteral = left instanceof BooleanLiteralExpr &&
							  right instanceof BooleanLiteralExpr;
			if (bothLiteral) {
				return;
			}

			var leftNotOrLiteral = isNotOrLiteral(left);
			var rightNotOrLiteral = isNotOrLiteral(right);

			if (leftNotOrLiteral && rightNotOrLiteral) {
				expr.setLeft(not(left));
				expr.setRight(not(right));
			} else if (leftNotOrLiteral) {
				expr.setOperator(op);
				expr.setLeft(not(left));
			} else if (rightNotOrLiteral) {
				expr.setOperator(op);
				expr.setRight(not(right));
			}
		});
	}

	private static boolean isNot(Expression expr) {
		return expr instanceof UnaryExpr unaryExpr &&
			   unaryExpr.getOperator() == LOGICAL_COMPLEMENT;
	}

	private static boolean isNotOrLiteral(Expression expr) {
		return isNot(expr) || expr instanceof BooleanLiteralExpr;
	}

	private static BinaryExpr.Operator flip(BinaryExpr.Operator operator) {
		return switch (operator) {
			case EQUALS -> NOT_EQUALS;
			case NOT_EQUALS -> EQUALS;
            default -> null;
		};
	}
}
