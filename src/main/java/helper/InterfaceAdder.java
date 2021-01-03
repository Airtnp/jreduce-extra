package helper;

import org.objectweb.asm.ClassVisitor;

import java.util.Arrays;
import java.util.HashSet;

import static org.objectweb.asm.Opcodes.ASM9;

public class InterfaceAdder extends ClassVisitor {
    private final HashSet<String> newInterfaces;

    public InterfaceAdder(ClassVisitor cv, HashSet<String> newInterfaces) {
        super(ASM9, cv);
        this.newInterfaces = newInterfaces;
    }

    public void visit(int version, int access,
                      String name, String signature,
                      String superName, String[] interfaces) {
        HashSet<String> ints = new HashSet<>(newInterfaces);
        ints.addAll(Arrays.asList(interfaces));
        cv.visit(version, access, name, signature,
                superName, (String[]) ints.toArray());
    }
}