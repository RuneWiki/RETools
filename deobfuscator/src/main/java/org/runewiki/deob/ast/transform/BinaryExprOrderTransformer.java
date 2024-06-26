package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import org.runewiki.deob.ast.util.ExprUtil;

public class BinaryExprOrderTransformer extends AstTransformer {
    @Override
    public void transformUnit(CompilationUnit unit) {
        unit.walk(node -> {
            if (!(node instanceof BinaryExpr expr)) {
                return;
            }

            var op = ExprUtil.flip(expr.getOperator());
            if (op == null) {
                return;
            }

            var type = expr.calculateResolvedType();
            if (op == BinaryExpr.Operator.PLUS && ExprUtil.isString(type)) {
                return;
            }

            var left = expr.getLeft();
            var right = expr.getRight();
            if (isLiteralOrThisExpr(left) && !isLiteralOrThisExpr(right)) {
                expr.setOperator(op);
                expr.setRight(left);
                expr.setLeft(right);
            }
        });
    }

    private boolean isLiteralOrThisExpr(Expression expr) {
        return expr instanceof LiteralExpr || expr instanceof ThisExpr;
    }
}
