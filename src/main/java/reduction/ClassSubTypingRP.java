package reduction;

public class ClassSubTypingRP extends ReductionPoint {
    public final String parentType;

    public ClassSubTypingRP(final int globalIndex, final String parentType) {
        super(ReductionPoint.CLASS_SUBTYPING, globalIndex);
        this.parentType = parentType;
    }
}
