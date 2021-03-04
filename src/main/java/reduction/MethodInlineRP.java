package reduction;

import org.objectweb.asm.tree.InsnList;

/**
 * Unimplemented
 */
public class MethodInlineRP extends ReductionPoint {
    public final InsnList targetInsns;

    public MethodInlineRP(final int globalIndex, final InsnList targetInsns) {
        super(ReductionPoint.METHOD_INLINE, globalIndex);
        this.targetInsns = targetInsns;
    }
}
