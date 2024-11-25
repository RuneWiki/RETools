package rs.lostcity.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;

import static com.github.javaparser.ast.expr.BinaryExpr.Operator.*;
import static rs.lostcity.deob.ast.util.ExprUtil.*;

public class ComplementTransformer extends AstTransformer {

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, BinaryExpr.class, expr -> {
			var op = complement(expr.getOperator());
			if (op == null) return;

			var left = expr.getLeft();
			var right = expr.getRight();
			var bothLiteral = isIntOrLongLiteral(left) && isIntOrLongLiteral(right);

			if (!bothLiteral && isComplementOrLiteral(left) && isComplementOrLiteral(right)) {
				expr.setOperator(op);
				expr.setLeft(complement(left));
				expr.setRight(complement(right));
			}
		});
	}

	private static boolean isComplement(Expression expr) {
		return expr instanceof UnaryExpr unaryExpr &&
			   unaryExpr.getOperator() == UnaryExpr.Operator.BITWISE_COMPLEMENT;
	}

	private static boolean isComplementOrLiteral(Expression expr) {
		return isComplement(expr) || isIntOrLongLiteral(expr);
	}

	private static BinaryExpr.Operator complement(BinaryExpr.Operator op) {
		return switch (op) {
			case EQUALS, NOT_EQUALS -> op;
			case GREATER -> LESS;
			case GREATER_EQUALS -> LESS_EQUALS;
			case LESS -> GREATER;
			case LESS_EQUALS -> GREATER_EQUALS;
			default -> null;
		};
	}

	private static Expression complement(Expression expr) {
		return switch (expr) {
			case UnaryExpr unaryExpr
					-> unaryExpr.getExpression().clone();

			case IntegerLiteralExpr intLitExpr
					-> toIntLiteralExpr(~checkedAsInt(intLitExpr));

			case LongLiteralExpr longLitExpr
					-> toLongLiteralExpr(~checkedAsLong(longLitExpr));

			default -> throw new IllegalArgumentException();
		};
	}
}
