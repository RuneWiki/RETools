package rs.lostcity.deob.bytecode.transform.openrs2;

import org.objectweb.asm.tree.ClassNode;
import rs.lostcity.asm.transform.Transformer;

import java.util.List;

public class CopyPropagationTransformer extends Transformer {
	private int propagatedLocals;

	@Override
	public void preTransform(List<ClassNode> classes) {
		propagatedLocals = 0;
	}

	@Override
	public void postTransform(List<ClassNode> classes) {
		System.out.println("Propagated " + propagatedLocals + " copies");
	}
}
