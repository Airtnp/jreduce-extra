package reduction;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;
import java.util.stream.Collectors;

public class ReductionPoint implements Comparable<ReductionPoint> {

    public final static int PARAMETER_SUBTYPING = 0;

    public final static int METHOD_INLINE = 1;

    public final static int METHOD_REMOVAL = 2;

    public final static int METHOD_NOP = 3;

    public final static int CLASS_SUBTYPING = 4;

    public final int sort;

    public final int globalIndex;

    ReductionPoint(final int sort, final int globalIndex) {
        this.sort = sort;
        this.globalIndex = globalIndex;
    }

    public int getType() {
        return this.sort;
    }

    public int getGlobalIndex() {
        return this.globalIndex;
    }

    public AbstractInsnNode getInsnNode() {
        if (this instanceof MethodRemovalRP) {
            final MethodRemovalRP mrrp = (MethodRemovalRP) this;
            mrrp.getInsnNode();
        }
        return null;
    }

    public void assignInsnNode(final InsnList insns) {
        if (this instanceof MethodRemovalRP) {
            final MethodRemovalRP mrrp = (MethodRemovalRP) this;
            mrrp.assignInsnNode(insns);
        }
        if (this instanceof MethodNopRP) {
            final MethodNopRP mrrp = (MethodNopRP) this;
            mrrp.assignInsnNode(insns);
        }
    }

    public void applyReduction(final InsnList insns, final Set<Integer> removedArgs) {
        if (this instanceof MethodRemovalRP) {
            final MethodRemovalRP mrrp = (MethodRemovalRP) this;
            mrrp.applyReduction(insns);
        }
        if (this instanceof MethodNopRP) {
            final MethodNopRP mrrp = (MethodNopRP) this;
            mrrp.applyReduction(insns, removedArgs);
        }
    }

    @Override
    public int compareTo(ReductionPoint o) {
        return Integer.compare(this.globalIndex, o.globalIndex);
    }
}
