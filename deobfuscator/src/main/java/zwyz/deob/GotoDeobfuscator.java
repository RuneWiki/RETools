package zwyz.deob;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

public class GotoDeobfuscator {
    public static void run(List<ClassNode> classes) {
        for (var clazz : classes) {
            for (var method : clazz.methods) {
                if (method.instructions.size() != 0) {
                    AsmUtil.removeUnreachableCode(method);

                    if (method.tryCatchBlocks.isEmpty()) {
                        sortBlocks(method);
                    }

                    AsmUtil.removeGotoNext(method);
                    AsmUtil.removeUnusedLabels(method);
                }
            }
        }
    }

    public static void sortBlocks(MethodNode method) {
        var newInstructions = new MethodNode();
        var sortedBlocks = sortBlocks(method.instructions);

        // Label all blocks
        for (var block : sortedBlocks) {
            if (block.instructions.isEmpty() || !(block.instructions.get(0) instanceof LabelNode)) {
                block.instructions.add(0, new LabelNode());
            }
        }

        // Jump to next block
        for (var block : sortedBlocks) {
            if (!AsmUtil.isTerminal(block.instructions.get(block.instructions.size() - 1))) {
                block.instructions.add(new JumpInsnNode(Opcodes.GOTO, ((LabelNode) block.next.instructions.get(0))));
            }
        }

        // Generate new code
        for (var block : sortedBlocks) {
            for (var instruction : block.instructions) {
                instruction.accept(newInstructions);
            }
        }

        method.instructions = newInstructions.instructions;
    }

    private static List<Block> sortBlocks(InsnList code) {
        var startBlock = new Block();
        Map<LabelNode, Block> labelBlocks = new HashMap<>();
        var currentBlock = startBlock;

        for (var instruction : code) {
            if (instruction instanceof LabelNode label) {
                var nextBlock = labelBlocks.computeIfAbsent(label, l -> new Block());

                if (currentBlock.instructions.isEmpty() || !AsmUtil.isTerminal(currentBlock.instructions.get(currentBlock.instructions.size() - 1))) {
                    currentBlock.next = nextBlock;
                    nextBlock.last = currentBlock;
                }

                currentBlock = nextBlock;
            }

            // Add the instruction
            currentBlock.instructions.add(instruction);

            // Link block to jump targets
            for (var target : AsmUtil.getJumpTargets(instruction)) {
                currentBlock.jumpTargets.add(labelBlocks.computeIfAbsent(target, k -> new Block()));
            }
        }

        List<Block> result = new ArrayList<>();
        sort(result, new HashSet<>(), startBlock);
        Collections.reverse(result);
        return result;
    }

    private static void sort(List<Block> result, Set<Block> visited, Block block) {
        if (!visited.add(block)) {
            return;
        }

        for (var jumpTarget : block.jumpTargets) {
            sort(result, visited, jumpTarget);
        }

        if (block.next != null) {
            sort(result, visited, block.next);
        }

        result.add(block);
    }

    public static class Block {
        public List<AbstractInsnNode> instructions = new ArrayList<>();
        public List<Block> jumpTargets = new ArrayList<>();
        public Block last;
        public Block next;
    }
}
