package org.runewiki.deob.ast.util;

import com.github.javaparser.ast.Node;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.function.Consumer;

import static com.github.javaparser.ast.Node.TreeTraversal;

public class NodeUtil {
	public static boolean isClass(ResolvedType type) {
		return type.isReferenceType() && type.asReferenceType().getQualifiedName().equals("java.lang.Class");
	}

	public static boolean isString(ResolvedType type) {
		return type.isReferenceType() && type.asReferenceType().getQualifiedName().equals("java.lang.String");
	}

	public static <T extends Node> void walk(Node node, Class<T> nodeType, Consumer<T> consumer) {
		node.walk(TreeTraversal.POSTORDER, n -> {
			if (nodeType.isAssignableFrom(n.getClass())) {
				consumer.accept(nodeType.cast(n));
			}
		});
	}
}
