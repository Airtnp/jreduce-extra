package jvm;

import graph.NopHierarchy;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;

public class NopMethodVisitor extends MethodVisitor {
    public final String clsName;
    private final boolean isInitial;
    private final boolean isReplaceAll;
    private final NopHierarchy hierarchy;
    private final HashSet<Integer> closure;
    private final MethodNode methodNode;

    public NopMethodVisitor(final int api, final String clsName,
                            final MethodNode mn,
                            final boolean isInitial, final boolean isReplaceAll,
                            final MethodVisitor mv, final NopHierarchy hierarchy, final HashSet<Integer> closure) {
        super(api, mv);
        this.clsName = clsName;
        this.isInitial = isInitial;
        this.isReplaceAll = isReplaceAll;
        this.hierarchy = hierarchy;
        this.closure = closure;
        this.methodNode = mn;
    }

    public void visitMethodInsnNative(int opcode, String owner, String name, String desc, boolean itf) {
        super.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    /**
     * Visits a method instruction. A method instruction is an instruction that invokes a method.
     *
     * @param opcode the opcode of the type instruction to be visited. This opcode is either
     *     INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or INVOKEINTERFACE.
     * @param owner the internal name of the method's owner class (see {@link
     *     Type#getInternalName()}).
     * @param name the method's name.
     * @param desc the method's descriptor (see {@link Type}).
     * @param itf if the method's owner class is an interface.
     */
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        if ("<init>".equals(name) || "<clinit>".equals(name)) {
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        } else {
            if (this.isReplaceAll) {
                hierarchy.resolveAllCall(this, this.closure, opcode, owner, name, desc, this.isInitial);
            } else {
                hierarchy.resolveStubCall(this, this.closure, opcode, owner, name, desc, this.isInitial);
            }
        }
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        hierarchy.visitEndMethod(this.isInitial);
    }
}
