package rs.lostcity.deob.bytecode.transform.openrs2;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import rs.lostcity.asm.transform.Transformer;

import java.util.List;

public class ConstantArgTransformer extends Transformer {
	private int branchesSimplified;
	private int constantsPropagated;

	@Override
	public void preTransform(List<ClassNode> classes) {
		branchesSimplified = 0;
		constantsPropagated = 0;
	}

	@Override
	public void postTransform(List<ClassNode> classes) {
		System.out.println("Simplified " + branchesSimplified + " branches and propagated " + constantsPropagated + " constants");
	}
}
