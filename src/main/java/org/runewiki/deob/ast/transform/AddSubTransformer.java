package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import org.runewiki.deob.ast.util.NodeUtil;

import java.util.ArrayList;
import java.util.List;

import static org.runewiki.deob.ast.util.ExprUtil.hasSideEffects;
import static org.runewiki.deob.ast.util.ExprUtil.negate;

public class AddSubTransformer extends AstTransformer {

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, BinaryExpr.class, expr -> {
			var op = expr.getOperator();
			if (op != BinaryExpr.Operator.PLUS && op != BinaryExpr.Operator.MINUS) {
				return;
			}

			var type = expr.calculateResolvedType();
			if (NodeUtil.isString(type)) {
				return;
			}

			var terms = new ArrayList<Expression>();
			addTerms(terms, expr, false);

			terms.sort((a, b) -> {
				// preserve the order of adjacent expressions with side effects
				var aHasSideEffects = hasSideEffects(a);
				var bHasSideEffects = hasSideEffects(b);
				if (aHasSideEffects && bHasSideEffects) {
					return 0;
				}

				// push negative expressions to the right so we can replace unary minus with binary minus
				var aNegative = isNegative(a);
				var bNegative = isNegative(b);
				if (aNegative && !bNegative) return 1;
				else if (!aNegative && bNegative) return -1;

				// push literals to the right
				var aLiteral = a instanceof LiteralExpr;
				var bLiteral = b instanceof LiteralExpr;
				if (aLiteral && !bLiteral) return 1;
				else if (!aLiteral && bLiteral) return -1;

				return 0;
			});

			var newExpr = terms.stream().reduce((left, right) -> {
				if (isNegative(right)) {
					return new BinaryExpr(left.clone(), negate(right), BinaryExpr.Operator.MINUS);
				} else {
					return new BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.PLUS);
				}
			}).orElseThrow();

			expr.replace(newExpr);
		});
	}

	private static void addTerms(List<Expression> terms, Expression expr, boolean negate) {
		switch (expr) {
			case UnaryExpr unaryExpr -> {
				if (unaryExpr.getOperator() == UnaryExpr.Operator.MINUS) {
					addTerms(terms, unaryExpr.getExpression(), !negate);
				} else if (negate) {
					terms.add(negate(expr));
				} else {
					terms.add(expr);
				}
			}
			case BinaryExpr binaryExpr -> {
				if (binaryExpr.getOperator() == BinaryExpr.Operator.PLUS) {
					addTerms(terms, binaryExpr.getLeft(), negate);
					addTerms(terms, binaryExpr.getRight(), negate);
				} else if (binaryExpr.getOperator() == BinaryExpr.Operator.MINUS) {
					addTerms(terms, binaryExpr.getLeft(), negate);
					addTerms(terms, binaryExpr.getRight(), !negate);
				} else if (negate) {
					terms.add(negate(expr));
				} else {
					terms.add(expr);
				}
			}
			default -> {
				if (negate) {
					terms.add(negate(expr));
				} else {
					terms.add(expr);
				}
			}
		}
	}

	private static boolean isNegative(Expression expr) {
		return switch (expr) {
			case UnaryExpr unaryExpr -> {
				yield unaryExpr.getOperator() == UnaryExpr.Operator.MINUS;
			}

			case IntegerLiteralExpr intLitExpr -> {
				var intNumber = intLitExpr.asNumber();
				if (intNumber.equals(IntegerLiteralExpr.MAX_31_BIT_UNSIGNED_VALUE_AS_LONG)) {
					yield false;
				} else if (intNumber instanceof Integer value) {
					yield value < 0;
				} else {
					throw new IllegalArgumentException("Invalid IntegerLiteralExpr type");
				}
			}

			case LongLiteralExpr longLitExpr -> {
				var longNumber = longLitExpr.asNumber();
				if (longNumber.equals(LongLiteralExpr.MAX_63_BIT_UNSIGNED_VALUE_AS_BIG_INTEGER)) {
					yield false;
				} else if (longNumber instanceof Long value) {
					yield value < 0;
				} else {
					throw new IllegalArgumentException("Invalid LongLiteralExpr type");
				}
			}

			default -> {
				yield false;
			}
		};
	}
}
