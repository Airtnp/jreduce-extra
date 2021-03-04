package reduction;

import helper.ASMUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MethodNopRP extends ReductionPoint {
    public final int insnOffset;
    public final MethodInsnNode originalInsnNode;
    public MethodInsnNode insnNode;

    public MethodNopRP(final int globalIndex, final int insnOffset, final AbstractInsnNode originalInsnNode) {
        super(ReductionPoint.METHOD_NOP, globalIndex);
        this.insnOffset = insnOffset;
        this.originalInsnNode = (MethodInsnNode) originalInsnNode;
    }

    public static InsnList generateInlinePop(int opcode, String desc, Set<Integer> removedArgs) {
        final InsnList newInsns = new InsnList();

        final boolean isStatic = opcode == Opcodes.INVOKESTATIC;
        final Type returnType = Type.getReturnType(desc);
        final List<Type> revArgTypes = Arrays.asList(Type.getArgumentTypes(desc));
        Collections.reverse(revArgTypes);

        // pop out arguments
        int idx = revArgTypes.size() - 1;
        // if not static, counting down from N (otherwise N - 1)
        if (!isStatic) {
            idx += 1;
        }
        for (final Type ty: revArgTypes) {
            if (!removedArgs.contains(idx)) {
                final int size = ty.getSize();
                if (size == 2) {
                    newInsns.add(new InsnNode(Opcodes.POP2));
                } else if (size == 1) {
                    newInsns.add(new InsnNode(Opcodes.POP));
                }
            }
            idx -= 1;
        }
        // pop out this reference if non-static & this not removed
        if (!isStatic && !removedArgs.contains(0)) {
            newInsns.add(new InsnNode(Opcodes.POP));
        }
        // add inline values
        switch (returnType.getSort()) {
            case Type.VOID:
                newInsns.add(new InsnNode(Opcodes.NOP));
                break;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                newInsns.add(new InsnNode(Opcodes.ICONST_0));
                break;
            case Type.LONG:
                newInsns.add(new InsnNode(Opcodes.LCONST_0));
                break;
            case Type.FLOAT:
                newInsns.add(new InsnNode(Opcodes.FCONST_0));
                break;
            case Type.DOUBLE:
                newInsns.add(new InsnNode(Opcodes.DCONST_0));
                break;
            default:
                newInsns.add(new InsnNode(Opcodes.ACONST_NULL));
                break;
        }

        return newInsns;
    }

    public AbstractInsnNode getInsnNode() {
        return originalInsnNode;
    }

    /**
     * Called after copying InsnList and before applying reduction & apply class writing
     * @param insns copied instruction lists
     */
    public void assignInsnNode(final InsnList insns) {
        this.insnNode = (MethodInsnNode) insns.get(insnOffset);
    }

    public void applyReduction(final InsnList insns, final Set<Integer> removedArgs) {
        final InsnList inlinePopInsns = generateInlinePop(
                insnNode.getOpcode(), insnNode.desc, removedArgs);
        // The insert will clear the generated sublist => for uniqueness of insn nodes
        // Directly operate on copy of the collector MethodNode
        insns.insert(insnNode, inlinePopInsns);
        insns.remove(insnNode);
    }
}
