package graph;

import java.util.Objects;

public class CallInfo {
    public final int opcode;
    public final String owner;
    public final String name;
    public final String desc;
    public final InlineClassMethod stubMethod;

    public CallInfo(final int opcode, final String owner, final String name, final String desc,
                    final InlineClassMethod stubMethod) {
        this.opcode = opcode;
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        this.stubMethod = stubMethod;
    }

    public boolean isStub() {
        return stubMethod != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallInfo callInfo = (CallInfo) o;
        return opcode == callInfo.opcode && Objects.equals(owner, callInfo.owner) && Objects.equals(name, callInfo.name) && Objects.equals(desc, callInfo.desc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(opcode, owner, name, desc);
    }
}
