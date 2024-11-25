package rs.lostcity.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;

public class EncloseTransformer extends AstTransformer {
	private enum Associativity { LEFT, RIGHT, NONE }

	private enum Op {
		ACCESS_PARENS(Associativity.LEFT),
		POSTFIX(Associativity.NONE),
		UNARY(Associativity.RIGHT),
		CAST_NEW(Associativity.RIGHT),
		MULTIPLICATIVE(Associativity.LEFT),
		ADDITIVE(Associativity.LEFT),
		SHIFT(Associativity.LEFT),
		RELATIONAL(Associativity.LEFT),
		EQUALITY(Associativity.NONE),
		BITWISE_AND(Associativity.LEFT),
		BITWISE_XOR(Associativity.LEFT),
		BITWISE_OR(Associativity.LEFT),
		LOGICAL_AND(Associativity.LEFT),
		LOGICAL_OR(Associativity.LEFT),
		TERNARY(Associativity.RIGHT),
		ASSIGNMENT(Associativity.RIGHT);

		private final Associativity associativity;

		Op(Associativity associativity) {
			this.associativity = associativity;
		}

		boolean isPrecedenceLess(Op other) {
			return ordinal() > other.ordinal();
		}

		boolean isPrecedenceLessEqual(Op other) {
			return ordinal() >= other.ordinal();
		}

		static Op from(Expression expr) {
			return switch (expr) {
				case ArrayAccessExpr arrayAccessExpr -> ACCESS_PARENS;
				case FieldAccessExpr fieldAccessExpr -> ACCESS_PARENS;
				case MethodCallExpr methodCallExpr -> ACCESS_PARENS;
				case EnclosedExpr enclosedExpr -> ACCESS_PARENS;
				case UnaryExpr unaryExpr -> unaryExpr.getOperator().isPostfix() ? POSTFIX : UNARY;
				case CastExpr castExpr -> CAST_NEW;
				case ObjectCreationExpr objectCreationExpr -> CAST_NEW;
				case ArrayCreationExpr arrayCreationExpr -> CAST_NEW;

				case BinaryExpr binaryExpr -> switch (binaryExpr.getOperator()) {
					case BinaryExpr.Operator.MULTIPLY -> MULTIPLICATIVE;
					case BinaryExpr.Operator.DIVIDE, BinaryExpr.Operator.REMAINDER -> MULTIPLICATIVE;
					case BinaryExpr.Operator.PLUS, BinaryExpr.Operator.MINUS -> ADDITIVE;
					case BinaryExpr.Operator.LEFT_SHIFT -> SHIFT;
					case BinaryExpr.Operator.SIGNED_RIGHT_SHIFT, BinaryExpr.Operator.UNSIGNED_RIGHT_SHIFT -> SHIFT;
					case BinaryExpr.Operator.LESS, BinaryExpr.Operator.LESS_EQUALS -> RELATIONAL;
					case BinaryExpr.Operator.GREATER, BinaryExpr.Operator.GREATER_EQUALS -> RELATIONAL;
					case BinaryExpr.Operator.EQUALS, BinaryExpr.Operator.NOT_EQUALS -> EQUALITY;
					case BinaryExpr.Operator.BINARY_AND -> BITWISE_AND;
					case BinaryExpr.Operator.XOR -> BITWISE_XOR;
					case BinaryExpr.Operator.BINARY_OR -> BITWISE_OR;
					case BinaryExpr.Operator.AND -> LOGICAL_AND;
					case BinaryExpr.Operator.OR -> LOGICAL_OR;
				};

				case InstanceOfExpr instanceOfExpr -> RELATIONAL;
				case ConditionalExpr conditionalExpr -> TERNARY;
				case AssignExpr assignExpr -> ASSIGNMENT;
				default -> null;
			};
		}
	}

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, Expression.class, expr -> {
			switch (expr) {
				case ArrayAccessExpr arrayAccessExpr -> encloseLeft(arrayAccessExpr, arrayAccessExpr.getName());
				case FieldAccessExpr fieldAccessExpr -> encloseLeft(fieldAccessExpr, fieldAccessExpr.getScope());
				case MethodCallExpr methodCallExpr ->
					methodCallExpr.getScope().ifPresent(
						scope -> encloseLeft(methodCallExpr, scope));

				case UnaryExpr unaryExpr -> encloseRight(unaryExpr, unaryExpr.getExpression());
				case CastExpr castExpr -> encloseRight(castExpr, castExpr.getExpression());
				case ObjectCreationExpr objectCreationExpr ->
					objectCreationExpr.getScope().ifPresent(
						scope -> encloseLeft(objectCreationExpr, scope));

				case BinaryExpr binaryExpr -> {
					encloseLeft(binaryExpr, binaryExpr.getLeft());
					encloseRight(binaryExpr, binaryExpr.getRight());
				}

				case InstanceOfExpr instanceOfExpr -> encloseLeft(instanceOfExpr, instanceOfExpr.getExpression());
				case ConditionalExpr conditionalExpr -> {
					encloseLeft(conditionalExpr, conditionalExpr.getCondition());
					encloseLeft(conditionalExpr, conditionalExpr.getThenExpr());
					encloseRight(conditionalExpr, conditionalExpr.getElseExpr());
				}

				case AssignExpr assignExpr -> {
					encloseLeft(assignExpr, assignExpr.getTarget());
					encloseRight(assignExpr, assignExpr.getValue());
				}

				default -> {}
			}
		});
	}

	private static void encloseLeft(Expression parent, Expression child) {
		var parentOp = Op.from(parent);
		if (parentOp == null) throw new IllegalArgumentException();

		var childOp = Op.from(child);
		if (childOp == null) return;

		switch (parentOp.associativity) {
			case Associativity.LEFT -> {
				if (childOp.isPrecedenceLess(parentOp)) {
					parent.replace(child, new EnclosedExpr(child.clone()));
				}
			}
			case Associativity.NONE, Associativity.RIGHT -> {
				if (childOp.isPrecedenceLessEqual(parentOp)) {
					parent.replace(child, new EnclosedExpr(child.clone()));
				}
			}
		}
	}

	private static void encloseRight(Expression parent, Expression child) {
		var parentOp = Op.from(parent);
		if (parentOp == null) throw new IllegalArgumentException();

		var childOp = Op.from(child);
		if (childOp == null) return;

		switch (parentOp.associativity) {
			case Associativity.RIGHT -> {
				if (childOp.isPrecedenceLess(parentOp)) {
					parent.replace(child, new EnclosedExpr(child.clone()));
				}
			}
			case Associativity.NONE, Associativity.LEFT -> {
				if (childOp.isPrecedenceLessEqual(parentOp)) {
					parent.replace(child, new EnclosedExpr(child.clone()));
				}
			}
		}
	}
}
