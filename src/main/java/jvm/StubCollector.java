package jvm;

import graph.ClassVertex;
import graph.Hierarchy;
import graph.InlineClassMethod;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;

import static org.objectweb.asm.Opcodes.ASM9;

public class StubCollector extends ClassNode {
    private final Hierarchy hierarchy;
    private final ClassVisitor cv;

    public StubCollector(final Hierarchy hierarchy, final ClassVisitor cv) {
        super(ASM9);
        this.cv = cv;
        this.hierarchy = hierarchy;
    }

    @Override
    public void visitEnd() {
        final ClassVertex cls = new ClassVertex(this.name, this.access,
                this.superName, new HashSet<>(this.interfaces));
        for (final MethodNode mn: this.methods) {
            // skip class constructors
            // XXX: jreduce stub constructors?
            if ("<init>".equals(mn.name) || "<clinit>".equals(mn.name)) {
                continue;
            }
            cls.addMethod(mn);

            final InsnList insns = mn.instructions;
            boolean validStub = false;
            InlineClassMethod.RetType ty = InlineClassMethod.RetType.Void;
            if (insns.size() == 1) {
                // void: return
                final AbstractInsnNode insn = insns.get(0);
                validStub = (insn.getOpcode() == Opcodes.RETURN);
            } else if (insns.size() == 2) {
                // non-void: aconst_null+areturn / xload0+xreturn / xipush 0+ireturn
                AbstractInsnNode value = insns.get(0);
                AbstractInsnNode ret = insns.get(1);
                if (value.getOpcode() == Opcodes.ACONST_NULL && ret.getOpcode() == Opcodes.ARETURN) {
                    validStub = true;
                    ty = InlineClassMethod.RetType.Ref;
                } else if (value.getOpcode() == Opcodes.ICONST_0 && ret.getOpcode() == Opcodes.IRETURN) {
                    validStub = true;
                    ty = InlineClassMethod.RetType.Int;
                } else if (value.getOpcode() == Opcodes.LCONST_0 && ret.getOpcode() == Opcodes.LRETURN) {
                    validStub = true;
                    ty = InlineClassMethod.RetType.Long;
                } else if (value.getOpcode() == Opcodes.FCONST_0 && ret.getOpcode() == Opcodes.FRETURN) {
                    validStub = true;
                    ty = InlineClassMethod.RetType.Float;
                } else if (value.getOpcode() == Opcodes.DCONST_0 && ret.getOpcode() == Opcodes.DRETURN) {
                    validStub = true;
                    ty = InlineClassMethod.RetType.Double;
                } else if (value.getOpcode() == Opcodes.BIPUSH && ret.getOpcode() == Opcodes.IRETURN) {
                    // even for non-zero, we can still remove it
                    // otherwise, cast value to IntInsnNode and get operand
                    validStub = true;
                    ty = InlineClassMethod.RetType.Byte;
                } else if (value.getOpcode() == Opcodes.SIPUSH && ret.getOpcode() == Opcodes.IRETURN) {
                    validStub = true;
                    ty = InlineClassMethod.RetType.Short;
                }
            }
            if (validStub) {
                cls.addStubMethod(mn, ty);
            }
        }
        this.hierarchy.addClass(cls);
        if (this.cv != null)
            accept(this.cv);
    }
}
