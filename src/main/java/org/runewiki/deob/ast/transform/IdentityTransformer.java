package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;

public class IdentityTransformer extends AstTransformer {

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, UnaryExpr.class, expr -> {
			switch (expr.getOperator()) {
				case PLUS -> {
					if (expr.getExpression() instanceof UnaryExpr inner) {
						if (inner.getOperator() == UnaryExpr.Operator.PLUS) {
							// +(+(...))
							expr.replace(inner.getExpression().clone());
						}
					}
				}
				case MINUS -> {
					if (expr.getExpression() instanceof UnaryExpr inner) {
						if (inner.getOperator() == UnaryExpr.Operator.MINUS) {
							// -(-(...))
							expr.replace(inner.getExpression().clone());
						}
					}
				}
			}
		});

		walk(unit, BinaryExpr.class, expr -> {
			switch (expr.getOperator()) {
				case PLUS -> {
					if (isZero(expr.getLeft())) {
						// 0 + x => x
						expr.replace(expr.getRight().clone());
					} else if (isZero(expr.getRight())) {
						// x + 0 => x
						expr.replace(expr.getLeft().clone());
					}
				}

				case MINUS -> {
					if (isZero(expr.getLeft())) {
						// 0 - x => -x
						expr.replace(new UnaryExpr(expr.getRight().clone(), UnaryExpr.Operator.MINUS));
					} else if (isZero(expr.getRight())) {
						// x - 0 => x
						expr.replace(expr.getLeft().clone());
					}
				}

				case MULTIPLY -> {
					if (isOne(expr.getLeft())) {
						// 1 * x => x
						expr.replace(expr.getRight().clone());
					} else if (isOne(expr.getRight())) {
						// x * 1 => x
						expr.replace(expr.getLeft().clone());
					}
				}

				case DIVIDE -> {
					if (isOne(expr.getRight())) {
						// x / 1 => x
						expr.replace(expr.getLeft().clone());
					}
				}
			}
		});

		walk(unit, AssignExpr.class, expr -> {
			var identity = switch (expr.getOperator()) {
                // x += 0, x -= 0
				case PLUS, MINUS -> isZero(expr.getValue());

                // x *= 1, x /= 1
				case MULTIPLY, DIVIDE -> isOne(expr.getValue());

				default -> false;
			};

			if (!identity) {
				return;
			}

			expr.getParentNode().ifPresent(parent -> {
				if (parent instanceof ExpressionStmt) {
					parent.remove();
				} else {
					expr.replace(expr.getTarget().clone());
				}
			});
		});
	}

	private boolean isZero(Expression expr) {
		return switch (expr) {
			case IntegerLiteralExpr intLitExpr -> intLitExpr.asNumber().intValue() == 0;
			case LongLiteralExpr longLitExpr -> longLitExpr.asNumber().longValue() == 0L;
            default -> false;
		};
	}

	private boolean isOne(Expression expr) {
		return switch (expr) {
			case IntegerLiteralExpr intLitExpr -> intLitExpr.asNumber().intValue() == 1;
			case LongLiteralExpr longLitExpr -> longLitExpr.asNumber().longValue() == 1L;
            default -> false;
		};
	}
}
