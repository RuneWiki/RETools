package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;

import java.util.Set;

import static org.runewiki.deob.ast.util.ExprUtil.*;

public class HexLiteralTransformer extends AstTransformer {

	private static final Set<BinaryExpr.Operator> SHIFT_OPS = Set.of(
		BinaryExpr.Operator.LEFT_SHIFT,
		BinaryExpr.Operator.SIGNED_RIGHT_SHIFT,
		BinaryExpr.Operator.UNSIGNED_RIGHT_SHIFT
	);

	private static final Set<BinaryExpr.Operator> BITWISE_OPS = Set.of(
		BinaryExpr.Operator.BINARY_AND,
		BinaryExpr.Operator.BINARY_OR,
		BinaryExpr.Operator.XOR
	);

	private static final Set<AssignExpr.Operator> ASSIGN_OPS = Set.of(
		AssignExpr.Operator.BINARY_AND,
		AssignExpr.Operator.BINARY_OR,
		AssignExpr.Operator.LEFT_SHIFT,
		AssignExpr.Operator.SIGNED_RIGHT_SHIFT,
		AssignExpr.Operator.UNSIGNED_RIGHT_SHIFT,
		AssignExpr.Operator.XOR
	);

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, BinaryExpr.class, expr -> {
			var operator = expr.getOperator();
			if (SHIFT_OPS.contains(operator)) {
				convertToHex(expr.getLeft());
			}
			if (BITWISE_OPS.contains(operator)) {
				convertToHex(expr.getLeft());
				convertToHex(expr.getRight());
			}
		});

		walk(unit, AssignExpr.class, expr -> {
			if (ASSIGN_OPS.contains(expr.getOperator())) {
				convertToHex(expr.getValue());
			}
		});
	}

	private void convertToHex(Expression expr) {
		switch (expr) {
			case IntegerLiteralExpr literal -> literal.replace(toHexLiteralExpr(checkedAsInt(literal)));
			case LongLiteralExpr literal -> literal.replace(toHexLiteralExpr(checkedAsLong(literal)));
			default -> { }
		}
	}
}
