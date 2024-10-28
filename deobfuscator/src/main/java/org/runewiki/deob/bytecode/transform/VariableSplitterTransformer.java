package org.runewiki.deob.bytecode.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.runewiki.asm.transform.Transformer;
import org.runewiki.deob.AsmUtil;

import java.util.*;

public class VariableSplitterTransformer extends Transformer {
    private Map<AbstractInsnNode, InstructionBlock> blocks = new HashMap<>();
    private Map<AbstractInsnNode, Set<AbstractInsnNode>> varInstructionGroups = new LinkedHashMap<>();
    private Set<AbstractInsnNode> parameterLoads = new HashSet<>();
    private InstructionBlock startBlock;
    private int firstLocalIndex;

    @Override
    public boolean transformMethod(List<ClassNode> classes, ClassNode clazz, MethodNode method) {
        if ((method.access & Opcodes.ACC_ABSTRACT) != 0) {
            return false;
        }

        blocks = new HashMap<>();
        varInstructionGroups = new LinkedHashMap<>();
        parameterLoads = new HashSet<>();
        startBlock = null;
        firstLocalIndex = -1;


        firstLocalIndex = AsmUtil.getFirstLocalIndex(method);
        startBlock = new InstructionBlock(null);
        startBlock.addNext(block(method.instructions.getFirst()));

        // Compute control flow edges between instructions
        for (var instruction : method.instructions) {
            var block = block(instruction);

            if (!AsmUtil.isTerminal(instruction)) {
                block.addNext(block(instruction.getNext()));
            }

            for (var target : AsmUtil.getJumpTargets(instruction)) {
                block.addNext(block(target));
            }
        }

        for (var tryCatch : method.tryCatchBlocks) {
            var instruction = (AbstractInsnNode) tryCatch.start;

            while (instruction != tryCatch.end) {
                block(instruction).addNext(block(tryCatch.handler));
                instruction = instruction.getNext();
            }
        }

        // Create a group for each instruction initially + cache load and store vars
        for (var instruction : method.instructions) {
            var block = block(instruction);
            block.load = AsmUtil.getLoadedVar(instruction);
            block.store = AsmUtil.getStoredVar(instruction);

            if (block.load != -1 || block.store != -1) {
                varInstructionGroups.put(instruction, Set.of(instruction));
            }
        }

        // Link var instructions that can load/store each others' values
        for (var instruction : method.instructions) {
            var block = block(instruction);

            if (block.load != -1) {
                for (var lastStore : findLastStores(block, block.load)) {
                    if (lastStore == startBlock) {
                        parameterLoads.add(block.instruction);
                    } else {
                        linkVarInstructions(block.instruction, lastStore.instruction);
                    }
                }
            }
        }

        // Assign a unique var index to each linked group
        var varIndex = AsmUtil.getFirstLocalIndex(method);

        for (var group : new LinkedHashSet<>(varInstructionGroups.values())) {
            var isParameter = false;
            var isSize2 = false;
            var var = -1;

            for (var instruction : group) {
                if (parameterLoads.contains(instruction)) {
                    isParameter = true;
                }

                if (instruction.getOpcode() == Opcodes.LSTORE || instruction.getOpcode() == Opcodes.DSTORE || instruction.getOpcode() == Opcodes.LLOAD || instruction.getOpcode() == Opcodes.DLOAD) {
                    isSize2 = true;
                }

                if (instruction instanceof VarInsnNode varInsn) {
                    if (var != -1 && var != varInsn.var) {
                        throw new IllegalStateException("trying to merge vars");
                    }

                    var = varInsn.var;
                }

                if (instruction instanceof IincInsnNode iincInsn) {
                    if (var != -1 && var != iincInsn.var) {
                        throw new IllegalStateException("trying to merge vars");
                    }

                    var = iincInsn.var;
                }
            }

            if (!isParameter) {
                for (var instruction : group) {
                    if (instruction instanceof VarInsnNode varInsn) {
                        varInsn.var = varIndex;
                    }

                    if (instruction instanceof IincInsnNode iincInsn) {
                        iincInsn.var = varIndex;
                    }
                }

                varIndex += isSize2 ? 2 : 1;
            }
        }

        method.maxLocals = varIndex;

        return false;
    }

    private Set<InstructionBlock> findLastStores(InstructionBlock block, int var) {
        var stores = new LinkedHashSet<InstructionBlock>();
        findLastStores(block, var, new HashSet<>(), stores);
        return stores;
    }

    private void findLastStores(InstructionBlock block, int var, Set<InstructionBlock> visited, Set<InstructionBlock> stores) {
        if (!visited.add(block)) {
            return;
        }

        for (var prev : block.prev) {
            if (prev.store == var || prev == startBlock && var < firstLocalIndex) {
                stores.add(prev); // add the store and stop here, previous stores are shadowed
            } else {
                findLastStores(prev, var, visited, stores);
            }
        }
    }

    private InstructionBlock block(AbstractInsnNode instruction) {
        return blocks.computeIfAbsent(instruction, InstructionBlock::new);
    }

    private void linkVarInstructions(AbstractInsnNode a, AbstractInsnNode b) {
        var setA = varInstructionGroups.get(a);
        var setB = varInstructionGroups.get(b);
        var setAB = new LinkedHashSet<AbstractInsnNode>(setA.size() + setB.size());
        setAB.addAll(setA);
        setAB.addAll(setB);

        for (var instruction : setAB) {
            varInstructionGroups.put(instruction, setAB);
        }
    }

    private static class InstructionBlock {
        public final AbstractInsnNode instruction;
        public List<InstructionBlock> prev = new ArrayList<>();
        public List<InstructionBlock> next = new ArrayList<>();
        public int load = -1;
        public int store = -1;

        public InstructionBlock(AbstractInsnNode instruction) {
            this.instruction = instruction;
        }

        public void addNext(InstructionBlock block) {
            next.add(block);
            block.prev.add(this);
        }
    }
}
