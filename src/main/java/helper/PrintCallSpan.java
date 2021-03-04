package helper;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.IOException;
import java.util.*;

public class PrintCallSpan {
    public static void print(String className, MethodNode mn) throws AnalyzerException {
        System.out.println(mn.name + ":");
        List<int[]> invocations = new ArrayList<>();
        final InsnList instructions = mn.instructions;

        IdentifyCallAlt identifyCall
                = IdentifyCallAlt.getInputs(className.replace('.', '/'), mn);

        for(int ix = 0, num = instructions.size(); ix < num; ix++) {
            AbstractInsnNode instr = instructions.get(ix);

            dumpFrame(instr, identifyCall.frames[ix]);

            if(instr.getType() != AbstractInsnNode.METHOD_INSN) continue;
            invocations.add(identifyCall.getSpan(instr));
        }

        printIt(invocations, instructions);
        System.out.println("------");
    }

    public static void print(String className, ClassReader r) throws AnalyzerException {
        ClassNode cn = new ClassNode();
        r.accept(cn, ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        for(MethodNode mn: cn.methods) {
            print(className, mn);
        }
    }

    private static void dumpFrame(AbstractInsnNode insn, Frame<SourceValue> frame) {
        System.out.print(textInstruction(insn));
        for (int i = 0; i < frame.getStackSize(); ++i) {
            System.out.println("\t\t" + i + ":");
            for (AbstractInsnNode fins: frame.getStack(i).insns) {
                System.out.println("\t\t" + textInstruction(fins));
            }
        }
    }

    private static String textInstruction(AbstractInsnNode insn) {
        Textifier toText = new Textifier();
        TraceMethodVisitor tmv = new TraceMethodVisitor(toText);
        insn.accept(tmv);
        return toText.text.get(0).toString();
    }

    private static void printIt(List<int[]> invocations, final InsnList instructions) {
        List<Level> levels = toTree(invocations);
        Textifier toText = new Textifier();
        TraceMethodVisitor tmv = new TraceMethodVisitor(toText);
        for(int ix = 0, num = instructions.size(); ix < num; ix++) {
            AbstractInsnNode instr = instructions.get(ix);
            boolean line = false;
            level: for(Level l: levels) {
                if(ix >= l.lo && ix <= l.hi) {
                    for(int[] b: l.branches) {
                        if(ix < b[0] || ix > b[1]) continue;
                        System.out.print(line?
                                (b[0] == ix? b[1] == ix? "─[": "┬─": b[1] == ix? "┴─": "┼─"):
                                (b[0] == ix? b[1] == ix? " [": "┌─": b[1] == ix? "└─": "│ "));
                        line |= b[0] == ix || b[1] == ix;
                        continue level;
                    }
                }
                System.out.print(line? "──": "  ");
            }
            instr.accept(tmv);
            System.out.print(toText.text.get(0));
            toText.text.clear();
        }
    }
    static class Level {
        int lo, hi;
        ArrayDeque<int[]> branches=new ArrayDeque<>();

        Level(int[] b) { lo=b[0]; hi=b[1]; branches.add(b); }
        boolean insert(int[] b) {
            if(b[1]<=lo) { branches.addFirst(b); lo=b[0]; }
            else if(b[0]>=hi) { branches.addLast(b); hi=b[1]; }
            else return b[0]>lo && b[1] < hi
                        && (b[0]+b[1])>>1 > (lo+hi)>>1? tryTail(b, lo, hi): tryHead(b, lo, hi);
            return true;
        }
        private boolean tryHead(int[] b, int lo, int hi) {
            int[] head=branches.removeFirst();
            try {
                if(head[1] > b[0]) return false;
                if(branches.isEmpty() || (lo=branches.getFirst()[0])>=b[1]) {
                    branches.addFirst(b);
                    return true;
                }
                else return b[0]>lo && b[1] < hi
                        && (b[0]+b[1])>>1 > (lo+hi)>>1? tryTail(b, lo, hi): tryHead(b, lo, hi);
            } finally { branches.addFirst(head); }
        }
        private boolean tryTail(int[] b, int lo, int hi) {
            int[] tail=branches.removeLast();
            try {
                if(tail[0] < b[1]) return false;
                if(branches.isEmpty() || (hi=branches.getLast()[1])<=b[0]) {
                    branches.addLast(b);
                    return true;
                }
                else return b[0]>lo && b[1] < hi
                        && (b[0]+b[1])>>1 > (lo+hi)>>1? tryTail(b, lo, hi): tryHead(b, lo, hi);
            } finally { branches.addLast(tail); }
        }
    }
    static List<Level> toTree(List<int[]> list) {
        if(list.isEmpty()) return Collections.emptyList();
        if(list.size()==1) return Collections.singletonList(new Level(list.get(0)));
        list.sort(Comparator.comparingInt(b -> b[1] - b[0]));
        ArrayList<Level> l=new ArrayList<>();
        insert: for(int[] b: list) {
            for(Level level: l) if(level.insert(b)) continue insert;
            l.add(new Level(b));
        }
        if(l.size() > 1) Collections.reverse(l);
        return l;
    }
}
