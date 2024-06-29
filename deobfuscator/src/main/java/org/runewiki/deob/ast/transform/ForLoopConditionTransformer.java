package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.ForStmt;

import java.util.Objects;

import static org.runewiki.deob.ast.util.ExprUtil.flip;
import static org.runewiki.deob.ast.util.ExprUtil.hasSideEffects;

public class ForLoopConditionTransformer extends AstTransformer {

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, ForStmt.class, stmt -> {
			var updatedExprs = stmt.getUpdate().stream()
					.map(ForLoopConditionTransformer::getUpdatedExpr)
					.filter(Objects::nonNull)
					.toList();

			stmt.getCompare().ifPresent(expr -> {
				if (!(expr instanceof BinaryExpr binaryExpr)) {
					return;
				} else if (hasSideEffects(binaryExpr)) {
					return;
				}

				var flipped = flip(binaryExpr.getOperator());
				if (flipped == null) return;

				if (!updatedExprs.contains(binaryExpr.getLeft()) && updatedExprs.contains(binaryExpr.getRight())) {
					stmt.setCompare(new BinaryExpr(binaryExpr.getRight().clone(), binaryExpr.getLeft().clone(), flipped));
				}
			});
		});
	}

	private static Expression getUpdatedExpr(Expression expr) {
		return switch (expr) {
			case UnaryExpr unaryExpr -> switch (unaryExpr.getOperator()) {
				case PREFIX_INCREMENT, PREFIX_DECREMENT,
					 POSTFIX_INCREMENT, POSTFIX_DECREMENT
						-> unaryExpr.getExpression();
                default -> null;
			};

			case AssignExpr assignExpr -> assignExpr.getTarget();

			default -> null;
		};
	}
}
