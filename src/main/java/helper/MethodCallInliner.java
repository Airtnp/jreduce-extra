package helper;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.*;

import static org.objectweb.asm.Opcodes.ASM9;

public class MethodCallInliner extends LocalVariablesSorter {
    private final String oldClass;
    private final String newClass;
    private final MethodNode mn;
    private final List<TryCatchBlockNode> blocks = new ArrayList<>();
    private boolean inlining;

    public MethodCallInliner(int access, String desc,
                             MethodVisitor mv, MethodNode mn,
                             String oldClass, String newClass) {
        super(ASM9, access, desc, mv);
        this.oldClass = oldClass;
        this.newClass = newClass;
        this.mn = mn;
        inlining = false;
    }

    public boolean shouldInline(int opcode, String owner, String name, String desc) {
        return name.equals(mn.name);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
        if (!shouldInline(opcode, owner, name, desc)) {
            mv.visitMethodInsn(opcode, owner, name, desc, isInterface);
            return;
        }

        final Map<String, String> map = Collections.singletonMap(oldClass, newClass);
        final Remapper remapper = new SimpleRemapper(map);
        final Label end = new Label();

        inlining = true;
        mn.instructions.resetLabels();
        mn.accept(new InlineAdapter(this, end,
                opcode == Opcodes.INVOKESTATIC ? Opcodes.ACC_STATIC : 0,
                desc, remapper));
        inlining = false;

        super.visitLabel(end);
    }


    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        if (!inlining) {
            blocks.add(new TryCatchBlockNode(
                    new LabelNode(start), new LabelNode(end),
                    new LabelNode(handler), type));
        } else {
            super.visitTryCatchBlock(start, end, handler, type);
        }
    }

    public void visitMaxs(int stack, int locals) {
        for (final TryCatchBlockNode b : blocks) {
            super.visitTryCatchBlock(
                    b.start.getLabel(), b.end.getLabel(),
                    b.handler.getLabel(), b.type);
        }
        super.visitMaxs(stack, locals);
    }

    public static class RemappingMethodAdapter extends LocalVariablesSorter {

        protected final Remapper remapper;

        public RemappingMethodAdapter(final int access, final String desc,
                                      final MethodVisitor mv, final Remapper remapper) {
            this(ASM9, access, desc, mv, remapper);
        }

        protected RemappingMethodAdapter(final int api, final int access,
                                         final String desc, final MethodVisitor mv, final Remapper remapper) {
            super(api, access, desc, mv);
            this.remapper = remapper;
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            AnnotationVisitor av = super.visitAnnotationDefault();
            return av == null ? av : new AnnotationRemapper(av, remapper);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            AnnotationVisitor av = super.visitAnnotation(remapper.mapDesc(desc),
                    visible);
            return av == null ? av : new AnnotationRemapper(av, remapper);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef,
                                                     TypePath typePath, String desc, boolean visible) {
            AnnotationVisitor av = super.visitTypeAnnotation(typeRef, typePath,
                    remapper.mapDesc(desc), visible);
            return av == null ? av : new AnnotationRemapper(av, remapper);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter,
                                                          String desc, boolean visible) {
            AnnotationVisitor av = super.visitParameterAnnotation(parameter,
                    remapper.mapDesc(desc), visible);
            return av == null ? av : new AnnotationRemapper(av, remapper);
        }

        @Override
        public void visitFrame(int type, int nLocal, Object[] local, int nStack,
                               Object[] stack) {
            super.visitFrame(type, nLocal, remapEntries(nLocal, local), nStack,
                    remapEntries(nStack, stack));
        }

        private Object[] remapEntries(int n, Object[] entries) {
            if (entries != null) {
                for (int i = 0; i < n; i++) {
                    if (entries[i] instanceof String) {
                        Object[] newEntries = new Object[n];
                        if (i > 0) {
                            System.arraycopy(entries, 0, newEntries, 0, i);
                        }
                        do {
                            Object t = entries[i];
                            newEntries[i++] = t instanceof String ? remapper
                                    .mapType((String) t) : t;
                        } while (i < n);
                        return newEntries;
                    }
                }
            }
            return entries;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name,
                                   String desc) {
            super.visitFieldInsn(opcode, remapper.mapType(owner),
                    remapper.mapFieldName(owner, name, desc),
                    remapper.mapDesc(desc));
        }

        @Override
        public void visitMethodInsn(final int opcode, final String owner,
                                    final String name, final String desc, final boolean itf) {
            if (api < Opcodes.ASM5) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }
            doVisitMethodInsn(opcode, owner, name, desc, itf);
        }

        private void doVisitMethodInsn(int opcode, String owner, String name,
                                       String desc, boolean itf) {
            // Calling super.visitMethodInsn requires to call the correct version
            // depending on this.api (otherwise infinite loops can occur). To
            // simplify and to make it easier to automatically remove the backward
            // compatibility code, we inline the code of the overridden method here.
            // IMPORTANT: THIS ASSUMES THAT visitMethodInsn IS NOT OVERRIDDEN IN
            // LocalVariableSorter.
            if (mv != null) {
                mv.visitMethodInsn(opcode, remapper.mapType(owner),
                        remapper.mapMethodName(owner, name, desc),
                        remapper.mapMethodDesc(desc), itf);
            }
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String desc, Handle bsm,
                                           Object... bsmArgs) {
            for (int i = 0; i < bsmArgs.length; i++) {
                bsmArgs[i] = remapper.mapValue(bsmArgs[i]);
            }
            super.visitInvokeDynamicInsn(
                    remapper.mapInvokeDynamicMethodName(name, desc),
                    remapper.mapMethodDesc(desc), (Handle) remapper.mapValue(bsm),
                    bsmArgs);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            super.visitTypeInsn(opcode, remapper.mapType(type));
        }

        @Override
        public void visitLdcInsn(Object cst) {
            super.visitLdcInsn(remapper.mapValue(cst));
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            super.visitMultiANewArrayInsn(remapper.mapDesc(desc), dims);
        }

        @Override
        public AnnotationVisitor visitInsnAnnotation(int typeRef,
                                                     TypePath typePath, String desc, boolean visible) {
            AnnotationVisitor av = super.visitInsnAnnotation(typeRef, typePath,
                    remapper.mapDesc(desc), visible);
            return av == null ? av : new AnnotationRemapper(av, remapper);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler,
                                       String type) {
            super.visitTryCatchBlock(start, end, handler, type == null ? null
                    : remapper.mapType(type));
        }

        @Override
        public AnnotationVisitor visitTryCatchAnnotation(int typeRef,
                                                         TypePath typePath, String desc, boolean visible) {
            AnnotationVisitor av = super.visitTryCatchAnnotation(typeRef, typePath,
                    remapper.mapDesc(desc), visible);
            return av == null ? av : new AnnotationRemapper(av, remapper);
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature,
                                       Label start, Label end, int index) {
            super.visitLocalVariable(name, remapper.mapDesc(desc),
                    remapper.mapSignature(signature, true), start, end, index);
        }

        @Override
        public AnnotationVisitor visitLocalVariableAnnotation(int typeRef,
                                                              TypePath typePath, Label[] start, Label[] end, int[] index,
                                                              String desc, boolean visible) {
            AnnotationVisitor av = super.visitLocalVariableAnnotation(typeRef,
                    typePath, start, end, index, remapper.mapDesc(desc), visible);
            return av == null ? av : new AnnotationRemapper(av, remapper);
        }
    }

    public static class InlineAdapter extends RemappingMethodAdapter {
        public final LocalVariablesSorter lvs;
        public final Label end;

        public InlineAdapter(LocalVariablesSorter mv, Label end,
                             int access, String desc, Remapper remapper) {
            super(access | Opcodes.ACC_STATIC, "()V", mv, remapper);
            this.lvs = mv;
            this.end = end;
            int offset = (access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;

            Type[] args = Type.getArgumentTypes(desc);
            for (int i = args.length - 1; i >= 0; i--) {
                super.visitVarInsn(args[i].getOpcode(Opcodes.ISTORE), i + offset);
            }
            if(offset > 0) {
                super.visitVarInsn(Opcodes.ASTORE, 0);
            }
        }

        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                super.visitJumpInsn(Opcodes.GOTO, end);
            } else {
                super.visitInsn(opcode);
            }
        }

        public void visitMaxs(int stack, int locals) {
            // ignore maxes
        }

        protected int newLocalMapping(Type type) {
            return lvs.newLocal(type);
        }
    }

}