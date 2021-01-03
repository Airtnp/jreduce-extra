package graph;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

public class ClassMethod {
    public final String className;
    public final String name;
    public final String descriptor;
    public final String signature;
    public final int access;
    public final boolean isStatic;
    public final boolean isAbstract;

    public ClassMethod(final String className, final String name, final String descriptor,
                       final String signature, final int access) {
        this.className = className;
        this.name = name;
        this.descriptor = descriptor;
        this.signature = signature;
        this.access = access;
        this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
        this.isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
    }

    public ClassMethod(final String className, final MethodNode mn) {
        this(className, mn.name, mn.desc, mn.signature, mn.access);
    }
}
