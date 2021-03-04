package helper;

import graph.InlineClassMethod;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.List;

import static org.objectweb.asm.Opcodes.ASM9;

public class DebugClass extends DebugSuperClass {
    int s = 0;
    boolean n = false;

    public static int staticMethod() {
        return 1;
    }

    public <T> HashSet<T> test(PrintWriter o, PrintWriter v, HashSet<T> dn) {
        String str = String.valueOf('c');
        for (int i = 0; i < s; ++i) {
            System.out.println(str);
            System.out.println(str.hashCode());
            Object obj;
            if (n) {
                o.write(str, 0, i);
                obj = Integer.valueOf(1 + s);
                o.write(String.valueOf('d'), Integer.valueOf(1 + s), Integer.valueOf(2 + s));
            } else {
                o.write(str, 0, 1);
                o.equals(v);
                obj = String.valueOf(staticMethod() + staticMethod());
            }
            System.out.println(obj.toString() + obj.hashCode());
            System.out.println(obj.hashCode());
        }
        return dn;
    }

    public static ClassReader getClassReader() throws IOException {
        Class<?> cls = MethodHandles.lookup().lookupClass();
        return new ClassReader(cls.getResourceAsStream(cls.getSimpleName() + ".class"));
    }

    public static MethodNode getMethodNode(String name) throws IOException {
        ClassReader cls = getClassReader();
        final MethodNode[] method = {null};
        cls.accept(new ClassNode(ASM9) {
            @Override
            public void visitEnd() {
                for (MethodNode mn: methods) {
                    if (mn.name.equals(name)) {
                        method[0] = mn;
                    }
                }
                super.visitEnd();
            }
        }, ClassReader.SKIP_DEBUG);
        return method[0];
    }
}
