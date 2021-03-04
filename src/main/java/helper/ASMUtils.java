package helper;

import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import reduction.ParamSubTypingRP;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ASMUtils {
    public static String printInsn(final AbstractInsnNode insn) {
        final StringWriter out = new StringWriter();
        final Printer printer = new Textifier();
        final TraceMethodVisitor methodPrinter = new TraceMethodVisitor(printer);
        insn.accept(methodPrinter);
        printer.print(new PrintWriter(out));
        return out.toString().replace('\n', ' ').trim()
                + " @{" + Integer.toHexString(System.identityHashCode(insn)) + "}";
    }

    public static void printInsnList(final InsnList insns) {
        int counter = 1;
        for (final AbstractInsnNode insn: insns) {
            System.out.println(counter + ":\t" + printInsn(insn));
            counter += 1;
        }
    }

    public static Map<LabelNode, LabelNode> cloneLabels(InsnList insns) {
        HashMap<LabelNode, LabelNode> labelMap = new HashMap();
        for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getType() == 8) {
                labelMap.put((LabelNode) insn, new LabelNode());
            }
        }
        return labelMap;
    }

    public static InsnList cloneInsnList(InsnList insns) {
        return cloneInsnList(cloneLabels(insns), insns);
    }

    public static InsnList cloneInsnList(Map<LabelNode, LabelNode> labelMap, InsnList insns) {
        InsnList clone = new InsnList();
        for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext()) {
            clone.add(insn.clone(labelMap));
        }

        return clone;
    }

    public static InsnList copyInsnList(final InsnList insns) {
        MethodNode mn = new MethodNode();
        insns.accept(mn);
        return mn.instructions;
    }

    public static Type getType(final String sigTypeName) {
        final String realName = "L" + sigTypeName.replace('.', '/') + ";";
        return Type.getType(realName);
    }

    public static String rewriteDescriptor(final String descriptor, final List<ParamSubTypingRP> rps) {
        final Type returnType = Type.getReturnType(descriptor);
        final Type[] argTypes = Type.getArgumentTypes(descriptor);
        for (final ParamSubTypingRP rp: rps) {
            argTypes[rp.paramIndex] = getType(rp.paramType);
        }
        return Type.getMethodDescriptor(returnType, argTypes);
    }

    public static String rewriteSignature(final String signature, final List<ParamSubTypingRP> rps) {
        if (signature == null) {
            return null;
        }
        final Map<Integer, String> params = new HashMap<>();
        for (final ParamSubTypingRP rp: rps) {
            params.put(rp.paramIndex, rp.paramType);
        }
        final SignatureWriter writer = new SignatureWriter() {
            int counter = 0;
            @Override
            public SignatureVisitor visitParameterType() {
                counter += 1;
                return super.visitParameterType();
            }

            @Override
            public void visitClassType(String name) {
                super.visitClassType(params.getOrDefault(counter - 1, name));
            }
        };
        final SignatureReader reader = new SignatureReader(signature);
        reader.accept(writer);
        return writer.toString();
    }
}
