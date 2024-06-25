package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;

public class IncrementTransformer extends AstTransformer {
    public void transformUnit(CompilationUnit unit) {
        unit.walk(stmt -> {
            if (!(stmt instanceof ExpressionStmt exprStmt)) {
                return;
            }

            if (!(exprStmt.getExpression() instanceof UnaryExpr expr)) {
                return;
            }

            expr.setOperator(toPostfix(expr.getOperator()));
        });

        unit.walk(stmt -> {
            if (!(stmt instanceof ForStmt forStmt)) {
                return;
            }

            for (Expression expr : forStmt.getUpdate()) {
                if (!(expr instanceof UnaryExpr unaryExpr)) {
                    continue;
                }

                unaryExpr.setOperator(toPostfix(unaryExpr.getOperator()));
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
