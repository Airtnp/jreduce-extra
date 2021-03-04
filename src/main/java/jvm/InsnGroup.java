package jvm;

import java.util.Set;
import java.util.stream.Collectors;

public class InsnGroup {
    public final InsnConsumer consumerInsn;
    public final Set<InsnConsumer> supplierInsns;

    public InsnGroup(final InsnConsumer consumerInsn, final Set<InsnConsumer> supplierInsns) {
        this.consumerInsn = consumerInsn;
        this.supplierInsns = supplierInsns;
    }

    @Override
    public String toString() {
        final String consumer = consumerInsn.toString();
        return consumer
                + " {\n\t"
                + supplierInsns.stream()
                    .map(InsnConsumer::toString)
                    .collect(Collectors.joining("\n\t"))
                + "\n}";
    }
}
