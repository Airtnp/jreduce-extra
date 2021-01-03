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

public class LibCollector extends ClassNode {
    private final Hierarchy hierarchy;
    private final ClassVisitor cv;

    public LibCollector(final Hierarchy hierarchy, final ClassVisitor cv) {
        super(ASM9);
        this.cv = cv;
        this.hierarchy = hierarchy;
    }

    @Override
    public void visitEnd() {
        final ClassVertex cls = new ClassVertex(this.name, this.access,
                this.superName, new HashSet<>(this.interfaces));
        this.hierarchy.addClass(cls);
        if (this.cv != null)
            accept(this.cv);
    }
}
