package jvm;

import helper.ASMUtils;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Objects;

/**
 * Normally a consumer will be a instruction, but for METHOD_INSN, we split the arguments
 */
public class InsnConsumer {
    public final AbstractInsnNode insn;
    public final int argNum;
    public final boolean isMethod;


    public InsnConsumer(final AbstractInsnNode insn, final int argNum, final boolean isMethod) {
        this.insn = insn;
        this.argNum = argNum;
        this.isMethod = isMethod;
    }

    public boolean isMethod() {
        return isMethod;
    }

    public static InsnConsumer fromNormalInsn(final AbstractInsnNode insn) {
        return new InsnConsumer(insn, -1, false);
    }

    public static InsnConsumer fromMethodInsn(final AbstractInsnNode insn, final int argNum) {
        return new InsnConsumer(insn, argNum, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InsnConsumer consumer = (InsnConsumer) o;
        return argNum == consumer.argNum && Objects.equals(insn, consumer.insn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(insn, argNum);
    }

    @Override
    public String toString() {
        if (isMethod()) {
            return "(" + ASMUtils.printInsn(insn) + "#" + argNum + ")";
        } else {
            return ASMUtils.printInsn(insn);
        }
    }
}
