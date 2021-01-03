package graph;

import jvm.StubCallVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InlineClassMethod extends ClassMethod {
    public enum RetType {
        Void,
        Int,
        Short,
        Byte,
        Char,
        Boolean,
        Long,
        Float,
        Double,
        Ref
    }

    public final RetType returnType;
    public final List<Type> revArgTypes;

    public InlineClassMethod(final String className, final String name, final String descriptor,
                             final String signature, final RetType returnType, final Type[] argTypes,
                             final int access) {
        super(className, name, descriptor, signature, access);
        this.returnType = returnType;
        this.revArgTypes = Arrays.asList(argTypes);
        Collections.reverse(this.revArgTypes);
    }

    public InlineClassMethod(final String className, final MethodNode mn,
                             final RetType returnType, final Type[] argTypes) {
        super(className, mn);
        this.returnType = returnType;
        this.revArgTypes = Arrays.asList(argTypes);
        Collections.reverse(this.revArgTypes);
    }

    public void generateInlineStub(final StubCallVisitor mv) {
        // pop out arguments
        for (final Type ty: this.revArgTypes) {
            final int size = ty.getSize();
            if (size == 2) {
                mv.visitInsn(Opcodes.POP2);
            } else if (size == 1) {
                mv.visitInsn(Opcodes.POP);
            }
        }
        // pop out this reference
        if (!isStatic) {
            mv.visitInsn(Opcodes.POP);
        }
        // add inline values
        switch (this.returnType) {
            case Void: break;
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
                mv.visitInsn(Opcodes.ICONST_0);
                break;
            case Long:
                mv.visitInsn(Opcodes.LCONST_0);
                break;
            case Float:
                mv.visitInsn(Opcodes.FCONST_0);
                break;
            case Double:
                mv.visitInsn(Opcodes.DCONST_0);
                break;
            case Ref:
                mv.visitInsn(Opcodes.ACONST_NULL);
                break;
        }
    }
}
