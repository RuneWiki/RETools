package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;

import com.github.javaparser.ast.stmt.*;
import java.util.*;

public class IncrementTransformer extends AstTransformer {
    public void transformUnit(CompilationUnit unit) {
        unit.walk(stmt -> {
            if (!(stmt instanceof ExpressionStmt)) {
                return;
            }

            Expression expr = ((ExpressionStmt) stmt).getExpression();
            if (!(expr instanceof UnaryExpr)) {
                return;
            }

            UnaryExpr unaryExpr = (UnaryExpr) expr;
            unaryExpr.setOperator(toPostfix(unaryExpr.getOperator()));
        });

        unit.walk(stmt -> {
            if (!(stmt instanceof ForStmt)) {
                return;
            }

            ForStmt forStmt = (ForStmt) stmt;
            for (Expression expr : forStmt.getUpdate()) {
                if (!(expr instanceof UnaryExpr)) {
                    continue;
                }

                UnaryExpr unaryExpr = (UnaryExpr) expr;
                unaryExpr.setOperator(toPostfix(unaryExpr.getOperator()));
            }
        });
    }

    private UnaryExpr.Operator toPostfix(UnaryExpr.Operator op) {
        switch (op) {
            case PREFIX_INCREMENT:
                return UnaryExpr.Operator.POSTFIX_INCREMENT;
            case PREFIX_DECREMENT:
                return UnaryExpr.Operator.POSTFIX_DECREMENT;
            default:
                return op;
        }
    }
}
