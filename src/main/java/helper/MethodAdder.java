package helper;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.ASM9;

public class MethodAdder extends ClassVisitor {
    private final int mAccess;
    private final String mName;
    private final String mDesc;
    private final String mSignature;
    private final String[] mExceptions;

    public MethodAdder(ClassVisitor cv,
                       int mthAccess, String mthName,
                       String mthDesc, String mthSignature,
                       String[] mthExceptions) {
        super(ASM9, cv);
        this.mAccess = mthAccess;
        this.mName = mthName;
        this.mDesc = mthDesc;
        this.mSignature = mthSignature;
        this.mExceptions = mthExceptions;
    }
    public void visitEnd() {
        MethodVisitor mv = cv.visitMethod(mAccess,
                mName, mDesc, mSignature, mExceptions);
        // create method body
        // FIXME: compute maxStack & maxLocals
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        super.visitEnd();
    }
}
