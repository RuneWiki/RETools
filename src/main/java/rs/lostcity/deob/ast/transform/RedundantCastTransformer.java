package rs.lostcity.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.resolution.MethodAmbiguityException;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.github.javaparser.resolution.types.ResolvedPrimitiveType.*;

public class RedundantCastTransformer extends AstTransformer {

	private static final Map<ResolvedType, Set<ResolvedType>> WIDENING_CONVERSIONS = new HashMap<>() {{
		put(BYTE, Set.of(SHORT, INT, LONG, FLOAT, DOUBLE));
		put(SHORT, Set.of(INT, LONG, FLOAT, DOUBLE));
		put(CHAR, Set.of(INT, LONG, FLOAT, DOUBLE));
		put(INT, Set.of(LONG, FLOAT, DOUBLE));
		put(LONG, Set.of(FLOAT, DOUBLE));
		put(FLOAT, Set.of(DOUBLE));
	}};

	private static final Map<ResolvedType, Set<ResolvedType>> NARROWING_CONVERSIONS = new HashMap<>() {{
		put(SHORT, Set.of(BYTE, CHAR));
		put(CHAR, Set.of(BYTE, SHORT));
		put(INT, Set.of(BYTE, SHORT, CHAR));
		put(LONG, Set.of(BYTE, SHORT, CHAR, INT));
		put(FLOAT, Set.of(BYTE, SHORT, CHAR, INT, LONG));
		put(DOUBLE, Set.of(BYTE, SHORT, CHAR, INT, LONG, FLOAT));
	}};

	@Override
	public void transformUnit(CompilationUnit unit) {
		// remove double casts
		walk(unit, CastExpr.class, expr -> {
			var innerExpr = expr.getExpression();
			if (innerExpr instanceof CastExpr castExpr && expr.getType().equals(castExpr.getType())) {
				expr.setExpression(castExpr.getExpression().clone());
			}
		});

		// remove null argument casts if the call remains unambiguous
		walk(unit, MethodCallExpr.class, expr -> {
			for (int i = 0; i < expr.getArguments().size(); i++) {
				var arg = expr.getArguments().get(i);

				if (!isCastedNull(arg)) {
					continue;
				} else if (resolvesVariadicAmbiguity(i, expr.resolve(), (CastExpr) arg)) {
					continue;
				}

				expr.getArguments().set(i, new NullLiteralExpr());

				try {
					expr.resolve();
				} catch (MethodAmbiguityException ex) {
					expr.getArguments().set(i, arg);
				}
			}
		});

		walk(unit, ObjectCreationExpr.class, expr -> {
			for (int i = 0; i < expr.getArguments().size(); i++) {
				var arg = expr.getArguments().get(i);

				if (!isCastedNull(arg)) {
					continue;
				} else if (resolvesVariadicAmbiguity(i, expr.resolve(), (CastExpr) arg)) {
					continue;
				}

				expr.getArguments().set(i, new NullLiteralExpr());

				try {
					expr.resolve();
				} catch (MethodAmbiguityException ex) {
					expr.getArguments().set(i, arg);
				}
			}
		});

		// remove null assignment casts
		walk(unit, VariableDeclarationExpr.class, expr -> {
			for (var variable : expr.getVariables()) {
				variable.getInitializer().ifPresent(initializer -> {
					if (isCastedNull(initializer)) {
						initializer.replace(new NullLiteralExpr());
					}
				});
			}
		});

		walk(unit, AssignExpr.class, expr -> {
			if (isCastedNull(expr.getValue())) {
				expr.setValue(new NullLiteralExpr());
			}
		});

		/*
		 * replace casts with widening/narrowing conversions
		 * see https://docs.oracle.com/javase/specs/jls/se11/html/jls-5.html
		 */
		walk(unit, CastExpr.class, expr -> {
			expr.getParentNode().ifPresent(parent -> {
				if (!(parent instanceof AssignExpr) && !(parent instanceof CastExpr)) {
					return;
				}

				var outerType = expr.getType();
				if (!outerType.isPrimitiveType()) {
					return;
				}

				var innerType = expr.getExpression().calculateResolvedType();
				if (!innerType.isPrimitive()) {
					return;
				}

				var resolvedOuterType = outerType.resolve();

				if (isWideningConversion(innerType, resolvedOuterType)) {
					expr.replace(expr.getExpression().clone());
				} else if (isNarrowingConversion(innerType, resolvedOuterType) && parent instanceof CastExpr) {
					expr.replace(expr.getExpression().clone());
				} else if (innerType == BYTE && resolvedOuterType == CHAR && parent instanceof CastExpr) {
					expr.replace(expr.getExpression().clone());
				}
			});
		});
	}

	private static boolean isWideningConversion(ResolvedType innerType, ResolvedType resolvedOuterType) {
		return WIDENING_CONVERSIONS.getOrDefault(innerType, Collections.emptySet()).contains(resolvedOuterType);
	}

	private static boolean isNarrowingConversion(ResolvedType innerType, ResolvedType resolvedOuterType) {
		return NARROWING_CONVERSIONS.getOrDefault(innerType, Collections.emptySet()).contains(resolvedOuterType);
	}

	private static boolean isCastedNull(Expression expr) {
		if (expr instanceof CastExpr castExpr) {
			return castExpr.getExpression() instanceof NullLiteralExpr;
		}
		return false;
	}

	private static boolean resolvesVariadicAmbiguity(int index, ResolvedMethodLikeDeclaration method, CastExpr cast) {
		if (index < method.getNumberOfParams()) {
			var param = method.getParam(index);
			return param.isVariadic() && param.getType().equals(cast.getType().resolve());
		}
		return false;
	}
}
