package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import org.runewiki.deob.ast.util.ExprUtil;

import java.util.Set;

import static com.github.javaparser.ast.expr.BinaryExpr.Operator.*;
import static java.lang.Character.*;

public class CharLiteralTransformer extends AstTransformer {

	private static final Set<BinaryExpr.Operator> COMPARISON_OPERATORS = Set.of(
		EQUALS, NOT_EQUALS, LESS, GREATER, LESS_EQUALS, GREATER_EQUALS
	);

	private static final Set<Integer> UNPRINTABLE_TYPES = Set.of(
		(int) UNASSIGNED,
		(int) LINE_SEPARATOR,
		(int) PARAGRAPH_SEPARATOR,
		(int) CONTROL,
		(int) FORMAT,
		(int) PRIVATE_USE,
		(int) SURROGATE
	);

	@Override
	public void transformUnit(CompilationUnit unit) {
		walk(unit, BinaryExpr.class, expr -> {
			if (COMPARISON_OPERATORS.contains(expr.getOperator())) {
				convertToCharLiteral(expr.getLeft(), expr.getRight());
				convertToCharLiteral(expr.getRight(), expr.getLeft());
			}
		});

		walk(unit, AssignExpr.class, expr -> {
			convertToCharLiteral(expr.getTarget(), expr.getValue());
		});
	}

	private static void convertToCharLiteral(Expression a, Expression b) {
		if (!(b instanceof IntegerLiteralExpr intLitExpr)) {
			return;
		}

		if (a.calculateResolvedType() != ResolvedPrimitiveType.CHAR) {
			return;
		}

		var n = ExprUtil.checkedAsInt(intLitExpr);
		if (n < 0) {
			char character = (char) -n;
			intLitExpr.replace(new UnaryExpr(new CharLiteralExpr(escape(character)), UnaryExpr.Operator.MINUS));
		} else {
			char character = (char) n;
			intLitExpr.replace(new CharLiteralExpr(escape(character)));
		}
	}

	private static String escape(char c) {
		// compatible with Fernflower's character escape code
		return switch (c) {
			case '\b' -> "\\b";
			case '\t' -> "\\t";
			case '\n' -> "\\n";
			case '\u000C' -> "\\f";
			case 'r' -> "\\r";
			case '\'' -> "'";
			case '\\' -> "\\";
            default -> {
				var type = Character.getType(c);
				if (UNPRINTABLE_TYPES.contains(type)) {
					yield "\\u" + "%04X".formatted((int) c);
				} else {
					yield Character.toString(c);
				}
			}
		};
	}
}
