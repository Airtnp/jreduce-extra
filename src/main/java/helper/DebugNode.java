package helper;

import graph.ClassMethod;
import graph.Hierarchy;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static org.objectweb.asm.Opcodes.ASM9;

public class DebugNode extends ClassNode {
    public Hierarchy hierarchy;

    public DebugNode() {
        super(ASM9);
        this.hierarchy = new Hierarchy();
    }

    @Override
    public void visitEnd() {
        for (MethodNode mn: methods) {
            ClassMethod cm = new ClassMethod(this.name, mn);
        }
        super.visitEnd();
    }
}
