package helper;

import org.objectweb.asm.ClassVisitor;

import static org.objectweb.asm.Opcodes.ASM9;

public class RemoveDebugAttributeAdapter extends ClassVisitor {

    public RemoveDebugAttributeAdapter(ClassVisitor cv) {
        super(ASM9, cv);
    }

    /*
    Remove SourceFile and SourceDebugExtenstion attributes
     */
    @Override
    public void visitSource(String source, String debug) { }

    /*
    Remove EnclosingMethod attribute
    FIXME: Can we remove EnclosingMethod attribute? ASM4 documentation says so
     */
    @Override
    public void visitOuterClass(String owner, String name, String desc) {
        super.visitOuterClass(owner, name, desc);
    }

    /*
    InnerClass attribute is ignored until JVM 10 by [JEP 181](http://openjdk.java.net/jeps/181).
    @ref:
     */
    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) { }
}