package org.runewiki.deob.ast.util;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.resolution.types.ResolvedType;

public class ExprUtil {
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

    public static boolean isString(ResolvedType type) {
        return type.isReferenceType() && type.asReferenceType().getQualifiedName().equals("java.lang.String");
    }
}
