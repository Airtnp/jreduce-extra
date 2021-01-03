package graph;

import java.util.Objects;

public class CallSite {
    public final String clsName;
    public final int clsIndex;
    public final int poolIndex;
    public final CallInfo callInfo;

    public CallSite(final String clsName, final int clsIndex, final int poolIndex, final CallInfo callInfo) {
        this.clsName = clsName;
        this.clsIndex = clsIndex;
        this.poolIndex = poolIndex;
        this.callInfo = callInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallSite callSite = (CallSite) o;
        return clsIndex == callSite.clsIndex && poolIndex == callSite.poolIndex && Objects.equals(clsName, callSite.clsName) && Objects.equals(callInfo, callSite.callInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clsName, clsIndex, poolIndex, callInfo);
    }

    @Override
    public String toString() {
        return String.valueOf(poolIndex);
    }
}
