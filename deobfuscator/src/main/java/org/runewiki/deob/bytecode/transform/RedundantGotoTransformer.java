package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.runewiki.asm.AsmUtil;
import org.runewiki.asm.transform.Transformer;

import java.util.*;

/*
 * Remove redundant GOTO instructions
 */
public class RedundantGotoTransformer extends Transformer {
    private int removedGoto = 0;
    private int removedLabel = 0;

    @Override
    public void preTransform(List<ClassNode> classes) {
        this.removedGoto = 0;
        this.removedLabel = 0;
    }

    @Override
    public boolean transformCode(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        // todo: remove unreachable code first?

        if (method.tryCatchBlocks.isEmpty()) {
            sortBlocks(method);
        }

        removeGotoNext(method);
        removeUnusedLabels(method);

        return false;
    }

    @Override
    public void postTransform(List<ClassNode> classes) {
        System.out.println("Removed " + this.removedGoto + " redundant GOTO instructions, " + this.removedLabel + " unused labels");
    }

    private void removeGotoNext(MethodNode method) {
        this.removedGoto += AsmUtil.removeIf(method.instructions, insn -> insn instanceof JumpInsnNode && insn.getOpcode() == Opcodes.GOTO && ((JumpInsnNode) insn).label == insn.getNext());
    }

    private void removeUnusedLabels(MethodNode method) {
        HashSet usedLabels = new HashSet<LabelNode>();

        for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
            usedLabels.add(tryCatch.start);
            usedLabels.add(tryCatch.end);
            usedLabels.add(tryCatch.handler);
        }

        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof JumpInsnNode) {
                usedLabels.add(((JumpInsnNode) insn).label);
            }

            if (insn instanceof TableSwitchInsnNode) {
                usedLabels.add(((TableSwitchInsnNode) insn).dflt);
                usedLabels.addAll(((TableSwitchInsnNode) insn).labels);
            }

            if (insn instanceof LookupSwitchInsnNode) {
                usedLabels.add(((LookupSwitchInsnNode) insn).dflt);
                usedLabels.addAll(((LookupSwitchInsnNode) insn).labels);
            }
        }

        this.removedLabel += AsmUtil.removeIf(method.instructions, insn -> insn instanceof LabelNode && !usedLabels.contains((LabelNode) insn));
    }

    private void sortBlocks(MethodNode method) {
        MethodNode newInstructions = new MethodNode();
        List<Block> sortedBlocks = sortBlocks(method.instructions);

        for (Block block : sortedBlocks) {
            if (block.instructions.isEmpty() || !(block.instructions.get(0) instanceof LabelNode)) {
                block.instructions.add(0, new LabelNode());
            }
        }

        for (Block block : sortedBlocks) {
            if (!AsmUtil.isTerminal(block.instructions.get(block.instructions.size() - 1))) {
                block.instructions.add(new JumpInsnNode(Opcodes.GOTO, ((LabelNode) block.next.instructions.get(0))));
            }
        }

        for (Block block : sortedBlocks) {
            for (AbstractInsnNode insn : block.instructions) {
                insn.accept(newInstructions);
            }
        }

        method.instructions = newInstructions.instructions;
    }

    private List<Block> sortBlocks(InsnList code) {
        Block startBlock = new Block();
        Map<LabelNode, Block> labelBlocks = new HashMap<>();
        Block currentBlock = startBlock;

        for (AbstractInsnNode insn : code) {
            if (insn instanceof LabelNode) {
                Block nextBlock = labelBlocks.computeIfAbsent((LabelNode) insn, l -> new Block());

                if (currentBlock.instructions.isEmpty() || !AsmUtil.isTerminal(currentBlock.instructions.get(currentBlock.instructions.size() - 1))) {
                    currentBlock.next = nextBlock;
                    nextBlock.last = currentBlock;
                }

                currentBlock = nextBlock;
            }

            currentBlock.instructions.add(insn);

            for (LabelNode target : AsmUtil.getJumpTargets(insn)) {
                currentBlock.jumpTargets.add(labelBlocks.computeIfAbsent(target, k -> new Block()));
            }
        }

        List<Block> result = new ArrayList<>();
        sort(result, new HashSet<>(), startBlock);
        Collections.reverse(result);
        return result;
    }

    private void sort(List<Block> result, Set<Block> visited, Block block) {
        if (!visited.add(block)) {
            return;
        }

        for (Block jumpTarget : block.jumpTargets) {
            sort(result, visited, jumpTarget);
        }

        if (block.next != null) {
            sort(result, visited, block.next);
        }

        result.add(block);
    }

    private static class Block {
        public List<AbstractInsnNode> instructions = new ArrayList<>();
        public List<Block> jumpTargets = new ArrayList<>();
        public Block last;
        public Block next;
    }
}
