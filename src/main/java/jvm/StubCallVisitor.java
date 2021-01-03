package jvm;

import graph.Hierarchy;
import org.objectweb.asm.MethodVisitor;

import java.util.HashSet;

public class StubCallVisitor extends MethodVisitor {
    public final String clsName;
    private final boolean isInitial;
    private final boolean isReplaceAll;
    private final Hierarchy hierarchy;
    private final HashSet<Integer> closure;

    public StubCallVisitor(final int api, final String clsName, final boolean isInitial, final boolean isReplaceAll,
                           final MethodVisitor mv, final Hierarchy hierarchy, final HashSet<Integer> closure) {
        super(api, mv);
        this.clsName = clsName;
        this.isInitial = isInitial;
        this.isReplaceAll = isReplaceAll;
        this.hierarchy = hierarchy;
        this.closure = closure;
    }

    public void visitMethodInsnNative(int opcode, String owner, String name, String desc, boolean itf) {
        super.visitMethodInsn(opcode, owner, name, desc, itf);
    }

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
