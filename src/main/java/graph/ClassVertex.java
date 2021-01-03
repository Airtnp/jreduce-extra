package graph;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.HashSet;

public class ClassVertex {
    public final String name;
    public final boolean isInterface;
    public final boolean isAbstract;
    public final String superCls;
    public final HashSet<String> interfaces;
    public final HashMap<ImmutablePair<String, String>, ClassMethod> methods;
    public final HashMap<ImmutablePair<String, String>, InlineClassMethod> stubMethods;

    public ClassVertex(final String name, final int access, String superCls, HashSet<String> interfaces) {
        this.name = name;
        this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        this.isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
        this.superCls = superCls;
        this.interfaces = interfaces;
        this.methods = new HashMap<>();
        this.stubMethods = new HashMap<>();
    }

    public void addMethod(final MethodNode mn) {
        methods.put(ImmutablePair.of(mn.name, mn.desc), new ClassMethod(this.name, mn));
    }

    public void addStubMethod(final MethodNode mn, final InlineClassMethod.RetType retType) {
        final Type[] argTypes = Type.getArgumentTypes(mn.desc);
        stubMethods.put(ImmutablePair.of(mn.name, mn.desc),
                new InlineClassMethod(this.name, mn, retType, argTypes));
    }

    public boolean isMethodNotAbstract(final ImmutablePair<String, String> nat) {
        final ClassMethod mthd = this.methods.get(nat);
        return !mthd.isAbstract;
    }

    public boolean isMethodNotStubbedOrAbstract(final ImmutablePair<String, String> nat) {
        final ClassMethod mthd = this.methods.get(nat);
        return !this.stubMethods.containsKey(nat) && !mthd.isAbstract;
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof  ClassVertex) && (toString().equals(obj.toString()));
    }

    @Override
    public String toString() {
        return this.name;
    }
}
