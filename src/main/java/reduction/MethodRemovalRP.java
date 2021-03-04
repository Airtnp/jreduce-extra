package reduction;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Set;
import java.util.stream.Collectors;

public class MethodRemovalRP extends ReductionPoint {
    public final int insnOffset;
    public final Set<Integer> dependentInsnOffsets;
    public MethodInsnNode insnNode;
    public Set<AbstractInsnNode> dependentInsns;

    public MethodRemovalRP(final int globalIndex, final int insnOffset,
                           final Set<Integer> dependentInsnOffsets) {
        super(ReductionPoint.METHOD_REMOVAL, globalIndex);
        this.insnOffset = insnOffset;
        this.dependentInsnOffsets = dependentInsnOffsets;
    }

    /**
     * Called after copying InsnList and before applying reduction & apply class writing
     * @param insns copied instruction lists
     */
    public void assignInsnNode(final InsnList insns) {
        final AbstractInsnNode insn = insns.get(insnOffset);
        if (!(insn instanceof MethodInsnNode)) {
            throw new IllegalStateException("MethodRemovalNp applies on a non-method call instruction");
        }
        this.insnNode = (MethodInsnNode) insn;
        this.dependentInsns = dependentInsnOffsets
                .stream()
                .map(insns::get).collect(Collectors.toSet());
    }

    public void applyReduction(final InsnList insns) {
        // XXX: ignored
    }
}
