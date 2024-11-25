package rs.lostcity.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import rs.lostcity.deob.ast.util.NodeUtil;

import static rs.lostcity.deob.ast.util.ExprUtil.flip;

public class BinaryExprOrderTransformer extends AstTransformer {

    @Override
    public void transformUnit(CompilationUnit unit) {
        walk(unit, BinaryExpr.class, expr -> {
            var op = flip(expr.getOperator());
            if (op == null) {
                return;
            }

            var type = expr.calculateResolvedType();
            if (op == BinaryExpr.Operator.PLUS && NodeUtil.isString(type)) {
                return;
            }

            var left = expr.getLeft();
            var right = expr.getRight();
            if (isLiteralOrThisExpr(left) && !isLiteralOrThisExpr(right)) {
                expr.setOperator(op);
                expr.setLeft(right.clone());
                expr.setRight(left.clone());
            }
        });
    }

    private boolean isLiteralOrThisExpr(Expression expr) {
        return expr instanceof LiteralExpr || expr instanceof ThisExpr;
    }
}
