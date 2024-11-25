package rs.lostcity.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import rs.lostcity.deob.ast.util.ExprUtil;

import java.util.Set;

public class BitMaskTransformer extends AstTransformer {

	private static final Set<BinaryExpr.Operator> RIGHT_SHIFT_OPS = Set.of(
		BinaryExpr.Operator.SIGNED_RIGHT_SHIFT,
		BinaryExpr.Operator.UNSIGNED_RIGHT_SHIFT
	);

	private static final Set<BinaryExpr.Operator> BITWISE_OPS = Set.of(
		BinaryExpr.Operator.BINARY_AND,
		BinaryExpr.Operator.BINARY_OR,
		BinaryExpr.Operator.XOR
	);

	@Override
	public void transformUnit(CompilationUnit unit) {
		/*
		 * Transform:
		 *
		 *     (x & y) >> z
		 *
		 * to:
		 *
		 *     (x >> z) & (y >> z)
		 *
		 * For example:
		 *
		 *     (x & 0xFF00) >> 8
		 *
		 * to:
		 *
		 *     (x >> 8) & 0xFF
		 */
		walk(unit, BinaryExpr.class, expr -> {
			var shiftOp = expr.getOperator();
			var bitwiseExpr = expr.getLeft();
			var shamtExpr = expr.getRight();

			if (!RIGHT_SHIFT_OPS.contains(shiftOp) || !(bitwiseExpr instanceof BinaryExpr bitwiseBinExpr) || !(shamtExpr instanceof IntegerLiteralExpr shamtLitExpr)) {
				return;
			}

			var bitwiseOp = bitwiseBinExpr.getOperator();
			var argExpr = bitwiseBinExpr.getLeft();
			var maskExpr = bitwiseBinExpr.getRight();

			if (!BITWISE_OPS.contains(bitwiseOp)) {
				return;
			}

			var shamt = ExprUtil.checkedAsInt(shamtLitExpr);
			switch (maskExpr) {
				case IntegerLiteralExpr intLitExpr -> {
					var mask = ExprUtil.checkedAsInt(intLitExpr);
					mask = switch (shiftOp) {
						case BinaryExpr.Operator.SIGNED_RIGHT_SHIFT -> mask >> shamt;
						case BinaryExpr.Operator.UNSIGNED_RIGHT_SHIFT -> mask >>> shamt;
                        default -> throw new RuntimeException("Invalid shiftOp");
					};
					maskExpr = ExprUtil.toIntLiteralExpr(mask);
				}
				case LongLiteralExpr longLitExpr -> {
					var mask = ExprUtil.checkedAsLong(longLitExpr);
					mask = switch (shiftOp) {
						case BinaryExpr.Operator.SIGNED_RIGHT_SHIFT -> mask >> shamt;
						case BinaryExpr.Operator.UNSIGNED_RIGHT_SHIFT -> mask >>> shamt;
                        default -> throw new RuntimeException("Invalid shiftOp");
					};
					maskExpr = ExprUtil.toLongLiteralExpr(mask);
				}
				default -> {
					return;
				}
			}

			expr.replace(new BinaryExpr(new BinaryExpr(argExpr.clone(), shamtExpr.clone(), shiftOp), maskExpr, bitwiseOp));
		});

		/*
		 * Transform:
		 *
		 *     (x << y) & z
		 *
		 * to:
		 *
		 *     (x & (z >>> y)) << y
		 *
		 * For example:
		 *
		 *     (x << 8) & 0xFF00
		 *
		 * to:
		 *
		 *     (x & 0xFF) << 8
		 */
		walk(unit, BinaryExpr.class, expr -> {
			var bitwiseOp = expr.getOperator();
			var shiftExpr = expr.getLeft();
			var maskExpr = expr.getRight();

			if (!BITWISE_OPS.contains(bitwiseOp) || !(shiftExpr instanceof BinaryExpr shiftBinExpr)) {
				return;
			}

			var shiftOp = shiftBinExpr.getOperator();
			var argExpr = shiftBinExpr.getLeft();
			var shamtExpr = shiftBinExpr.getRight();

			if (shiftOp != BinaryExpr.Operator.LEFT_SHIFT || !(shamtExpr instanceof IntegerLiteralExpr shamtLitExpr)) {
				return;
			}

			var shamt = ExprUtil.checkedAsInt(shamtLitExpr);

			Expression newMaskExpr;
			switch (maskExpr) {
				case IntegerLiteralExpr intLitExpr -> {
					var mask = ExprUtil.checkedAsInt(intLitExpr);
					if (shamt > Integer.numberOfTrailingZeros(mask)) {
						return;
					}
					mask = mask >>> shamt;
					newMaskExpr = ExprUtil.toIntLiteralExpr(mask);
				}
				case LongLiteralExpr longLitExpr -> {
					var mask = ExprUtil.checkedAsLong(longLitExpr);
					if (shamt > Long.numberOfTrailingZeros(mask)) {
						return;
					}
					mask = mask >>> shamt;
					newMaskExpr = ExprUtil.toLongLiteralExpr(mask);
				}
				default -> {
					return;
				}
			}

			expr.replace(new BinaryExpr(new BinaryExpr(argExpr.clone(), newMaskExpr, bitwiseOp), shamtExpr.clone(), shiftOp));
		});
	}
}
