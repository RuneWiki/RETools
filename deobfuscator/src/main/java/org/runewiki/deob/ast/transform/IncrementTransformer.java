package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;

public class IncrementTransformer extends AstTransformer {

    @Override
    public void transformUnit(CompilationUnit unit) {
        walk(unit, ExpressionStmt.class, stmt -> {
			if (stmt.getExpression() instanceof UnaryExpr unaryExpr) {
				unaryExpr.setOperator(toPostfix(unaryExpr.getOperator()));
			}
		});

        walk(unit, ForStmt.class, forStmt -> {
            for (Expression expr : forStmt.getUpdate()) {
				if (expr instanceof UnaryExpr unaryExpr) {
					unaryExpr.setOperator(toPostfix(unaryExpr.getOperator()));
				}
			}
        });
    }

    private UnaryExpr.Operator toPostfix(UnaryExpr.Operator op) {
        return switch (op) {
            case PREFIX_INCREMENT -> UnaryExpr.Operator.POSTFIX_INCREMENT;
            case PREFIX_DECREMENT -> UnaryExpr.Operator.POSTFIX_DECREMENT;
            default -> op;
        };
    }
}
