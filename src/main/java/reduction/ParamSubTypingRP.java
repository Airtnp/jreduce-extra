package reduction;

public class ParamSubTypingRP extends ReductionPoint {
    public final int paramIndex;
    // Should be the internal version inside the method description
    public final String paramType;

    public ParamSubTypingRP(final int globalIndex,
                            final int paramIndex, final String paramType) {
        super(ReductionPoint.PARAMETER_SUBTYPING, globalIndex);
        this.paramIndex = paramIndex;
        this.paramType = paramType;
    }
}
