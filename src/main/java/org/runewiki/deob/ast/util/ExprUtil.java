package org.runewiki.deob.ast.util;

import com.github.javaparser.ast.expr.*;

public class ExprUtil {
	public static boolean isIntOrLongLiteral(Expression expr) {
		return expr instanceof IntegerLiteralExpr ||
			   expr instanceof LongLiteralExpr;
	}

    public static int checkedAsInt(IntegerLiteralExpr expr) {
		if (expr.asNumber() instanceof Integer value) return value;
		throw new IllegalArgumentException("Invalid IntegerLiteralExpr type");
	}

    public static long checkedAsLong(LongLiteralExpr expr) {
		if (expr.asNumber() instanceof Long value) return value;
		throw new IllegalArgumentException("Invalid LongLiteralExpr type");
	}

	public static IntegerLiteralExpr toIntLiteralExpr(int value) {
		return new IntegerLiteralExpr(Integer.toString(value));
	}

	public static LongLiteralExpr toLongLiteralExpr(long value) {
		return new LongLiteralExpr(value + "L");
	}

	public static IntegerLiteralExpr toHexLiteralExpr(int value) {
		return new IntegerLiteralExpr("0x" + Integer.toUnsignedString(value, 16).toUpperCase());
	}

	public static LongLiteralExpr toHexLiteralExpr(long value) {
		return new LongLiteralExpr("0x" + Long.toUnsignedString(value, 16).toUpperCase() + "L");
	}

    public static BinaryExpr.Operator flip(BinaryExpr.Operator op) {
        return switch (op) {
            case PLUS, MULTIPLY -> op;
            case EQUALS, NOT_EQUALS -> op;
            case BINARY_AND, BINARY_OR -> op;
            case XOR, OR, AND -> op;
            case GREATER -> BinaryExpr.Operator.LESS;
            case GREATER_EQUALS -> BinaryExpr.Operator.LESS_EQUALS;
            case LESS -> BinaryExpr.Operator.GREATER;
            case LESS_EQUALS -> BinaryExpr.Operator.GREATER_EQUALS;
            default -> null;
        };
    }

	public static Expression negate(Expression expr) {
		return switch (expr) {
			case UnaryExpr unaryExpr -> switch (unaryExpr.getOperator()) {
				case UnaryExpr.Operator.PLUS -> new UnaryExpr(unaryExpr.getExpression().clone(), UnaryExpr.Operator.MINUS);
				case UnaryExpr.Operator.MINUS -> unaryExpr.getExpression().clone();
				default -> new UnaryExpr(unaryExpr.clone(), UnaryExpr.Operator.MINUS);
			};

			case IntegerLiteralExpr intLitExpr -> {
				var intNumber = intLitExpr.asNumber();
				if (intNumber.equals(IntegerLiteralExpr.MAX_31_BIT_UNSIGNED_VALUE_AS_LONG)) {
					yield toIntLiteralExpr(Integer.MIN_VALUE);
				} else if (intNumber instanceof Integer value) {
					yield toIntLiteralExpr(-value);
				} else {
					throw new IllegalArgumentException("Invalid IntegerLiteralExpr type");
				}
			}

			case LongLiteralExpr longLitExpr -> {
				var longNumber = longLitExpr.asNumber();
				if (longNumber.equals(LongLiteralExpr.MAX_63_BIT_UNSIGNED_VALUE_AS_BIG_INTEGER)) {
					yield toLongLiteralExpr(Long.MIN_VALUE);
				} else if (longNumber instanceof Long value) {
					yield toLongLiteralExpr(-value);
				} else {
					throw new IllegalArgumentException("Invalid LongLiteralExpr type");
				}
			}

			default -> new UnaryExpr(expr.clone(), UnaryExpr.Operator.MINUS);
		};
	}

	public static Expression not(Expression expr) {
		switch (expr) {
			case UnaryExpr unaryExpr -> {
				if (unaryExpr.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
					return unaryExpr.getExpression().clone();
				}
			}

			case BinaryExpr binaryExpr -> {
				var left = binaryExpr.getLeft();
				var right = binaryExpr.getRight();
				switch (binaryExpr.getOperator()) {
					case EQUALS -> {
						return new BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.NOT_EQUALS);
					}
					case NOT_EQUALS -> {
						return new BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.EQUALS);
					}
					case GREATER -> {
						return new BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.LESS_EQUALS);
					}
					case GREATER_EQUALS -> {
						return new BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.LESS);
					}
					case LESS -> {
						return new BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.GREATER_EQUALS);
					}
					case LESS_EQUALS -> {
						return new BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.GREATER);
					}
					case AND -> {
						return new BinaryExpr(not(left), not(right), BinaryExpr.Operator.OR);
					}
					case OR -> {
						return new BinaryExpr(not(left), not(right), BinaryExpr.Operator.AND);
					}
				}
			}

			case BooleanLiteralExpr boolLitExpr -> {
				return new BooleanLiteralExpr(!boolLitExpr.getValue());
			}

			default -> {}
		}

		return new UnaryExpr(expr.clone(), UnaryExpr.Operator.LOGICAL_COMPLEMENT);
	}

	public static int countNots(Expression expr) {
		var count = 0;

		if (expr instanceof UnaryExpr unaryExpr && unaryExpr.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
			count++;
		} else if (expr instanceof BinaryExpr binaryExpr && binaryExpr.getOperator() == BinaryExpr.Operator.NOT_EQUALS) {
			count++;
		}

		for (var child : expr.findAll(Expression.class)) {
			if (!child.equals(expr)) {
				count += countNots(child);
			}
		}

		return count;
	}

	public static boolean hasSideEffects(Expression expr) {
		return switch (expr) {
			case LiteralExpr literalExpr
					-> false;

			case NameExpr nameExpr
					-> false;

			case ThisExpr thisExpr
					-> false;

			case UnaryExpr unaryExpr
					-> hasSideEffects(unaryExpr.getExpression());

			case BinaryExpr binaryExpr
					-> hasSideEffects(binaryExpr.getLeft()) ||
					   hasSideEffects(binaryExpr.getRight());

			case ArrayAccessExpr arrayAccessExpr
					-> hasSideEffects(arrayAccessExpr.getName()) ||
					   hasSideEffects(arrayAccessExpr.getIndex());

			case FieldAccessExpr fieldAccessExpr
					-> hasSideEffects(fieldAccessExpr.getScope());

			case EnclosedExpr enclosedExpr
					-> hasSideEffects(enclosedExpr.getInner());

			case CastExpr castExpr
					-> hasSideEffects(castExpr.getExpression());

			case InstanceOfExpr instanceOfExpr
					-> hasSideEffects(instanceOfExpr.getExpression());

			case ConditionalExpr conditionalExpr
					-> hasSideEffects(conditionalExpr.getCondition()) ||
					   hasSideEffects(conditionalExpr.getThenExpr()) ||
					   hasSideEffects(conditionalExpr.getElseExpr());

			// TODO(gpe): more cases
			default -> true;
		};
	}
}
