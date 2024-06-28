package org.runewiki.deob.ast.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.VoidType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.runewiki.deob.ast.util.ExprUtil.countNots;
import static org.runewiki.deob.ast.util.ExprUtil.not;

public class IfElseTransformer extends AstTransformer {
	private static final int IF_DEINDENT_THRESHOLD = 5;

	@Override
	public void transformUnit(CompilationUnit unit) {
		CompilationUnit oldUnit;
		do {
			oldUnit = unit.clone();
			transform(unit);
		} while (!unit.equals(oldUnit));
	}

	private void transform(CompilationUnit unit) {
		walk(unit, IfStmt.class, stmt -> {
			stmt.getElseStmt().ifPresent(elseStmt -> {
				var condition = stmt.getCondition();
				var thenStmt = stmt.getThenStmt();
				if (isIf(thenStmt) && !isIf(elseStmt)) {
					/*
					 * Rewrite:
					 *
					 * if (a) {
					 *     if (b) {
					 *         ...
					 *     }
					 * } else {
					 *     ...
					 * }
					 *
					 * to:
					 *
					 * if (!a) {
					 *     ...
					 * } else {
					 *     if (b) {
					 *         ...
					 *     }
					 * }
					 */
					stmt.setCondition(not(condition));
					stmt.setThenStmt(elseStmt.clone());
					stmt.setElseStmt(thenStmt.clone());
				} else if (!isIf(thenStmt) && isIf(elseStmt)) {
					/*
					 * Don't consider any more conditions for swapping the
					 * if/else branches, as it'll introduce another level of
					 * indentation.
					 */
					return;
				}

				/*
				 * Prefer fewer NOTs in the if condition
				 *
				 * Rewrites:
				 *
				 * if (!a) {
				 *     ...
				 * } else {
				 *     ....
				 * }
				 *
				 * to:
				 *
				 * if (a) {
				 *     ...
				 * } else {
				 *     ...
				 * }
				 *
				 */
				var notCondition = not(condition);
				if (countNots(notCondition) < countNots(condition)) {
					stmt.setCondition(notCondition);
					if (elseStmt instanceof IfStmt) {
						var block = new BlockStmt();
						block.getStatements().add(elseStmt.clone());
						stmt.setThenStmt(block);
					} else {
						stmt.setThenStmt(elseStmt.clone());
					}
					stmt.setElseStmt(thenStmt.clone());
				}
			});
		});

		/*
		 * Rewrite:
		 *
		 * } else {
		 *     if (a) {
		 *         ...
		 *     }
		 * }
		 *
		 * to:
		 *
		 * } else if (a) {
		 *     ....
		 * }
		 */
		walk(unit, IfStmt.class, stmt -> {
			stmt.getElseStmt().ifPresent(elseStmt -> {
				var ifStmt = getIf(elseStmt);
				if (ifStmt != null) {
					stmt.setElseStmt(ifStmt);
				}
			});
		});

		/*
		 * Rewrite:
		 *
		 * } else {
		 *     if (!a) {
		 *         ...
		 *         throw ...; // or return
		 *     }
		 *     ...
		 * }
		 *
		 * to:
		 *
		 * } else if (a) {
		 *     ...
		 * } else {
		 *     ...
		 *     throw ...; // or return
		 * }
		 */
		walk(unit, IfStmt.class, stmt -> {
			stmt.getElseStmt().ifPresent(elseStmt -> {
				// match
				if (!(elseStmt instanceof BlockStmt blockStmt)) {
					return;
				}

				var statements = blockStmt.getStatements();
				if (statements.isEmpty()) {
					return;
				}

				var ifStmt = statements.getFirst().orElse(null);
				if (!(ifStmt instanceof IfStmt iff)) {
					return;
				} else if (iff.getElseStmt().isPresent()) {
					return;
				}

				var thenStmt = iff.getThenStmt();
				if (!isTailThrowOrReturn(thenStmt)) {
					return;
				}

				// rewrite
				var condition = not(iff.getCondition());

				var tail = blockStmt.clone();
				tail.getStatements().removeFirst();

				elseStmt.replace(new IfStmt(condition, tail, thenStmt.clone()));
			});
		});

		/*
		 * Rewrite:
		 *
		 * } else {
		 *     return a ? ... : ...;
		 * }
		 *
		 * to:
		 *
		 * } else if (a) {
		 *     return ...;
		 * } else {
		 *     return ...;
		 * }
		 */
		walk(unit, IfStmt.class, stmt -> {
			stmt.getElseStmt().ifPresent(elseStmt -> {
				// match
				if (!(elseStmt instanceof BlockStmt blockStmt)) {
					return;
				}

				var stmts = blockStmt.getStatements();
				if (stmts.size() != 1) {
					return;
				}

				var head = stmts.getFirst().orElseThrow();
				if (!(head instanceof ReturnStmt returnStmt)) {
					return;
				}

				returnStmt.getExpression().ifPresent(expr -> {
					if (!(expr instanceof ConditionalExpr condExpr)) {
						return;
					}

					// replace
					var thenBlock = new BlockStmt(new NodeList<>(new ReturnStmt(condExpr.getThenExpr())));
					var elseBlock = new BlockStmt(new NodeList<>(new ReturnStmt(condExpr.getElseExpr())));
					stmt.setElseStmt(new IfStmt(condExpr.getCondition(), thenBlock, elseBlock));
				});
			});
		});

		/*
		 * Rewrite:
		 *
		 * if (a) {
		 *     if (b) {
		 *         ...
		 *     }
		 * }
		 *
		 * to:
		 *
		 * if (a && b) {
		 *     ...
		 * }
		 */
		walk(unit, IfStmt.class, outerStmt -> {
			if (outerStmt.getElseStmt().isPresent()) {
				return;
			}

			var innerStmt = getIf(outerStmt.getThenStmt());
			if (innerStmt == null) {
				return;
			}

			if (innerStmt.getElseStmt().isPresent()) {
				return;
			}

			outerStmt.setCondition(new BinaryExpr(outerStmt.getCondition(), innerStmt.getCondition(), BinaryExpr.Operator.AND));
			outerStmt.setThenStmt(innerStmt.getThenStmt());
		});

		walk(unit, MethodDeclaration.class, method -> {
			if (!(method.getType() instanceof VoidType)) {
				return;
			}

			method.getBody().ifPresent(body -> {
				var ifStmt = body.getStatements().getLast().orElse(null);
				if (ifStmt == null) {
					return;
				}

				if (!(ifStmt instanceof IfStmt iff)) {
					return;
				}

				var thenStatements = iff.getThenStmt().findAll(Statement.class).size();
				iff.getElseStmt().ifPresentOrElse(elseStmt -> {
					if (isIf(elseStmt)) {
						return;
					}

					var elseStatements = elseStmt.findAll(Statement.class).size();
					if (thenStatements <= IF_DEINDENT_THRESHOLD && elseStatements <= IF_DEINDENT_THRESHOLD) {
						return;
					}

					/*
					 * Rewrite:
					 *
					 * void m(...) {
					 *     ...
					 *     if (a) {
					 *         ...
					 *     } else {
					 *         ...
					 *     }
					 * }
					 *
					 * to:
					 *
					 * void m(...) {
					 *     ...
					 *     if (!a) { // or `if (a)`, depending on which arm is smaller
					 *         ...
					 *         return;
					 *     }
					 *     ...
					 * }
					 */
					if (elseStatements > thenStatements) {
						body.getStatements().addAll(flatten(elseStmt));

						iff.setThenStmt(appendReturn(iff.getThenStmt()));
						iff.removeElseStmt();
					} else {
						body.getStatements().addAll(flatten(iff.getThenStmt()));

						iff.setCondition(not(iff.getCondition()));
						iff.setThenStmt(appendReturn(elseStmt));
						iff.removeElseStmt();
					}
				}, () -> {
					/*
					 * Rewrite:
					 *
					 * void m(...) {
					 *     ...
					 *     if (a) {
					 *         ...
					 *     }
					 * }
					 *
					 * to:
					 *
					 * void m(...) {
					 *     ...
					 *     if (!a) {
					 *         return;
					 *     }
					 *     ...
					 * }
					 */
					if (thenStatements <= IF_DEINDENT_THRESHOLD) {
						return;
					}

					body.getStatements().addAll(flatten(iff.getThenStmt()));

					iff.setCondition(not(iff.getCondition()));
					iff.setThenStmt(new BlockStmt(new NodeList<>(new ReturnStmt())));
				});
			});
		});

		/*
		 * Rewrite:
		 *
		 * if (a) {
		 *     ...
		 *     throw ...; // or return
		 * } else {
		 *     ...
		 * }
		 *
		 * to:
		 *
		 * if (a) { // or `if (!a)`, if the arms are swapped
		 *     ...
		 *     throw ...; // or return
		 * }
		 * ...
		 */
		class Counter { int index = 0; }
		walk(unit, BlockStmt.class, blockStmt -> {
			/*
			 * XXX(gpe): need to iterate through blockStmt.stmts manually as we
			 * insert extra statements during iteration (ugh!)
			 */
			var counter = new Counter();
			while (counter.index < blockStmt.getStatements().size()) {
				var ifStmt = blockStmt.getStatements().get(counter.index);
				if (!(ifStmt instanceof IfStmt iff)) {
					counter.index++;
					continue;
				}

				iff.getElseStmt().ifPresent(elseStmt -> {
					if (isIf(elseStmt)) {
						return;
					}

					/*
					 * If one of the arms consists of just a throw, move that
					 * into an if regardless of the fact that the method as a
					 * whole will end up longer.
					 */
					if (isThrow(iff.getThenStmt())) {
						blockStmt.getStatements().addAll(counter.index + 1, flatten(elseStmt));

						iff.removeElseStmt();

						return;
					} else if (isThrow(elseStmt)) {
						blockStmt.getStatements().addAll(counter.index + 1, flatten(iff.getThenStmt()));

						iff.setCondition(not(iff.getCondition()));
						iff.setThenStmt(appendReturn(elseStmt));
						iff.removeElseStmt();

						return;
					}

					var thenStatements = iff.getThenStmt().findAll(Statement.class).size();
					var elseStatements = elseStmt.findAll(Statement.class).size();
					if (thenStatements <= IF_DEINDENT_THRESHOLD && elseStatements <= IF_DEINDENT_THRESHOLD) {
						return;
					}

					if (elseStatements > thenStatements && isTailThrowOrReturn(iff.getThenStmt())) {
						blockStmt.getStatements().addAll(counter.index + 1, flatten(elseStmt));

						iff.removeElseStmt();
					} else if (isTailThrowOrReturn(elseStmt)) {
						blockStmt.getStatements().addAll(counter.index + 1, flatten(iff.getThenStmt()));

						iff.setCondition(not(iff.getCondition()));
						iff.setThenStmt(appendReturn(elseStmt));
						iff.removeElseStmt();
					}
				});

				counter.index++;
			}
		});
	}

	private static Statement appendReturn(Statement stmt) {
		if (stmt instanceof BlockStmt blockStmt) {
			var last = blockStmt.getStatements().getLast().orElse(null);
			if (last instanceof ReturnStmt || last instanceof ThrowStmt) {
				return stmt.clone();
			} else {
				var list = new NodeList<Statement>();
				for (var statement : blockStmt.getStatements()) {
					list.add(statement.clone());
				}
				list.add(new ReturnStmt());
				return new BlockStmt(list);
			}
		} else if (stmt instanceof ReturnStmt || stmt instanceof ThrowStmt) {
			return stmt.clone();
		} else {
			return new BlockStmt(new NodeList<>(stmt.clone(), new ReturnStmt()));
		}
	}

	private static Collection<Statement> flatten(Statement stmt) {
		if (stmt instanceof BlockStmt blockStmt) {
			var list = new ArrayList<Statement>();
			for (var statement : blockStmt.getStatements()) {
				list.add(statement.clone());
			}
			return list;
		} else {
			return List.of(stmt.clone());
		}
	}

	private static boolean isIf(Statement stmt) {
		return getIf(stmt) != null;
	}

	private static IfStmt getIf(Statement stmt) {
		return switch (stmt) {
			case IfStmt ifStmt -> ifStmt.clone();

			case BlockStmt blockStmt -> {
				var stmts = blockStmt.getStatements();
				var head = stmts.size() == 1 ? stmts.getFirst().orElseThrow() : null;
				if (head instanceof IfStmt iff) {
					yield iff.clone();
				} else {
					yield null;
				}
			}

			default -> null;
		};
	}

	private static boolean isThrow(Statement stmt) {
		return switch (stmt) {
			case ThrowStmt throwStmt -> true;
			case BlockStmt blockStmt -> {
				var stmts = blockStmt.getStatements();
				if (stmts.size() == 1) {
					yield stmts.getFirst().orElseThrow() instanceof ThrowStmt;
				} else {
					yield false;
				}
			}
			default -> false;
		};
	}

	private static boolean isTailThrowOrReturn(Statement stmt) {
		return switch (stmt) {
			case ThrowStmt throwStmt -> true;
			case ReturnStmt returnStmt -> true;
			case BlockStmt blockStmt -> {
				var tail = blockStmt.getStatements().getLast().orElse(null);
				yield tail instanceof ThrowStmt || tail instanceof ReturnStmt;
			}
			default -> false;
		};
	}
}
