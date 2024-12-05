package rs.lostcity.deob.bytecode.transform.openrs2;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import rs.lostcity.asm.InsnNodeUtil;
import rs.lostcity.asm.transform.Transformer;

import java.util.List;

public class RedundantGotoTransformer extends Transformer {
	private int removed;

	@Override
	public void preTransform(List<ClassNode> classes) {
		removed = 0;
	}

	@Override
	public boolean transformMethod(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
		InsnNodeUtil.removeDeadCode(method, clazz.name);
		return false;
	}

	@Override
	public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
		for (var insn : method.instructions) {
			if (insn.getOpcode() == Opcodes.GOTO && InsnNodeUtil.getNextReal(insn) == InsnNodeUtil.getNextReal(((JumpInsnNode) insn).label)) {
				method.instructions.remove(insn);
				removed++;
			}
		}

		return false;
	}

	@Override
	public void postTransform(List<ClassNode> classes) {
		System.out.println("Removed " + removed + " redundant GOTO instructions");
	}
}
