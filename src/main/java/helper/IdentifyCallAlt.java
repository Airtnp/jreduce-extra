package helper;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;

public class IdentifyCallAlt {
    static IdentifyCallAlt getInputs(
            String internalClassName, MethodNode toAnalyze) throws AnalyzerException {

        Map<AbstractInsnNode, Set<AbstractInsnNode>> sources = new HashMap<>();
        SourceInterpreter i = new SourceInterpreter();
        Analyzer<SourceValue> analyzer = new Analyzer<>(i);
        return new IdentifyCallAlt(toAnalyze.instructions, analyzer.analyze(internalClassName, toAnalyze));
    }
    public final InsnList instructions;
    public final Frame<SourceValue>[] frames;

    private IdentifyCallAlt(InsnList il, Frame<SourceValue>[] analyzed) {
        instructions = il;
        frames = analyzed;
    }
    int[] getSpan(AbstractInsnNode i) {
        MethodInsnNode mn = (MethodInsnNode)i;
        // can't use getArgumentsAndReturnSizes, as for the frame, double and long do not count as 2
        int nArg = mn.desc.startsWith("()")? 0: Type.getArgumentTypes(mn.desc).length;
        int end = instructions.indexOf(mn);
        Frame<SourceValue> f = frames[end];
        SourceValue receiver = f.getStack(f.getStackSize() - nArg - 1);
        if(receiver.insns.size() != 1) {
            return new int[] {end, end};
        }
        AbstractInsnNode n = receiver.insns.iterator().next();
        // if(n.getOpcode() != Opcodes.ALOAD && n.getOpcode() != Opcodes.GETSTATIC)
        //    throw new UnsupportedOperationException(""+n.getOpcode());
        return new int[] { instructions.indexOf(n), end };
    }
}