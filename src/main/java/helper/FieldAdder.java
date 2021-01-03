package helper;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.FieldNode;

import static org.objectweb.asm.Opcodes.ASM9;

public class FieldAdder extends ClassVisitor {
    private final FieldNode fn;

    public FieldAdder(ClassVisitor cv, FieldNode fn) {
        super(ASM9, cv);
        this.fn = fn;
    }

    public void visitEnd() {
        fn.accept(cv);
        super.visitEnd();
    }
}