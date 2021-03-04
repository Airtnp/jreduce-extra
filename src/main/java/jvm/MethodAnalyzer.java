package jvm;

import graph.Hierarchy;
import graph.InlineClassMethod;
import asm.tree.analysis.SourceInterpreter;
import asm.tree.analysis.Analyzer;
import helper.ASMUtils;
import helper.GlobalConfig;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import reduction.MethodNopRP;
import reduction.ParamSubTypingRP;
import reduction.RPGroup;
import reduction.ReductionPoint;
import sun.reflect.generics.parser.SignatureParser;
import sun.reflect.generics.tree.ClassTypeSignature;
import sun.reflect.generics.tree.MethodTypeSignature;
import sun.reflect.generics.tree.TypeSignature;

import java.util.*;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * Method Rewriter for reducing method instructions
 */
public class MethodAnalyzer extends MethodNode {
    public final ClassAnalyzeOptions options;
    public final String className;
    public final MethodNode methodNode;
    public final Hierarchy hierarchy;
    public UnionInsnMap insnMap;
    public RPGroup rpSection;
    public Attribute attribute;

    public final List<RPGroup> rpGroups;
    public final Map<Integer, AbstractInsnNode> rpPoints;
    public final Map<AbstractInsnNode, Integer> rpReversePoints;

    public static class Attribute {
        public final boolean isStub;
        public final InlineClassMethod.RetType stubType;

        public Attribute(final Pair<Boolean, InlineClassMethod.RetType> stubType) {
            this.isStub = stubType.getLeft();
            this.stubType = stubType.getRight();
        }
    }

    public MethodAnalyzer(int access, String name, String descriptor, String signature, String[] exceptions,
                          final String className, final Hierarchy hierarchy, final ClassAnalyzeOptions options) {
        super(ASM9, access, name, descriptor, signature, exceptions);
        this.className = className;
        this.methodNode = this;
        this.hierarchy = hierarchy;
        this.options = options;
        this.rpGroups = new ArrayList<>();
        this.rpPoints = new TreeMap<>();
        this.rpReversePoints = new HashMap<>();
    }

    public void prepare() {
        final int low = hierarchy.getCurrentIndex();
        if (options.doReduction) {
            this.analyze();
        }
        final int high = hierarchy.getCurrentIndex();
        this.rpSection = new RPGroup(low, high, 0);
        this.attribute = new Attribute(getStubType(methodNode));
    }

    private Pair<Boolean, InlineClassMethod.RetType> getStubType(final MethodNode methodNode) {
        // skip class constructors
        if ("<init>".equals(methodNode.name) || "<clinit>".equals(methodNode.name)) {
            return ImmutablePair.of(false, InlineClassMethod.RetType.Void);
        }

        final InsnList insns = methodNode.instructions;
        boolean validStub = false;
        InlineClassMethod.RetType ty = InlineClassMethod.RetType.Void;
        if (insns.size() == 1) {
            // void: return
            final AbstractInsnNode insn = insns.get(0);
            validStub = (insn.getOpcode() == Opcodes.RETURN);
        } else if (insns.size() == 2) {
            // non-void: aconst_null+areturn / xload0+xreturn / xipush 0+ireturn
            AbstractInsnNode value = insns.get(0);
            AbstractInsnNode ret = insns.get(1);
            if (value.getOpcode() == Opcodes.ACONST_NULL && ret.getOpcode() == Opcodes.ARETURN) {
                validStub = true;
                ty = InlineClassMethod.RetType.Ref;
            } else if (value.getOpcode() == Opcodes.ICONST_0 && ret.getOpcode() == Opcodes.IRETURN) {
                validStub = true;
                ty = InlineClassMethod.RetType.Int;
            } else if (value.getOpcode() == Opcodes.LCONST_0 && ret.getOpcode() == Opcodes.LRETURN) {
                validStub = true;
                ty = InlineClassMethod.RetType.Long;
            } else if (value.getOpcode() == Opcodes.FCONST_0 && ret.getOpcode() == Opcodes.FRETURN) {
                validStub = true;
                ty = InlineClassMethod.RetType.Float;
            } else if (value.getOpcode() == Opcodes.DCONST_0 && ret.getOpcode() == Opcodes.DRETURN) {
                validStub = true;
                ty = InlineClassMethod.RetType.Double;
            } else if (value.getOpcode() == Opcodes.BIPUSH && ret.getOpcode() == Opcodes.IRETURN) {
                // even for non-zero, we can still remove it
                // otherwise, cast value to IntInsnNode and get operand
                validStub = true;
                ty = InlineClassMethod.RetType.Byte;
            } else if (value.getOpcode() == Opcodes.SIPUSH && ret.getOpcode() == Opcodes.IRETURN) {
                validStub = true;
                ty = InlineClassMethod.RetType.Short;
            }
        }
        return ImmutablePair.of(validStub, ty);
    }

    public void analyzeParameter() {
        final boolean isGeneric = methodNode.signature != null;
        // Parse signature for potential reduced arguments
        final String signature = isGeneric ? methodNode.signature : methodNode.desc;
        final MethodTypeSignature sig = SignatureParser.make().parseMethodSig(signature);
        // XXX: Assume all type invariant here
        // XXX: Therefore, only consider raw Object types with their parents
        // ClassTypeSignature: Objects
        // TypeVariableSignature: Generic types
        // ArrayTypeSignature: Array types
        // BaseType: own signatures
        final List<Pair<Integer, String>> potentialArguments = new ArrayList<>();
        int index = 0;
        for (final TypeSignature ty: sig.getParameterTypes()) {
            if (ty instanceof ClassTypeSignature) {
                final ClassTypeSignature cty = (ClassTypeSignature)ty;
                // Don't consider inner inner classes (A.B.C...) / generic classes
                if (cty.getPath().size() == 1 && cty.getPath().get(0).getTypeArguments().length == 0) {
                    potentialArguments.add(ImmutablePair.of(index, cty.getPath().get(0).getName()));
                }
            }
            index += 1;
        }

        // XXX: thinking about how to use calledBases after successfully running
        // Enhance: could use the result after removing methods
        final Set<String> calledBases = new HashSet<>();
        // Parse instructions for potential owner callings to avoid invalid reductions
        for (final AbstractInsnNode insn: methodNode.instructions) {
            if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
                final MethodInsnNode methodInsn = (MethodInsnNode) insn;
                final String potentialBase = methodInsn.owner;
                calledBases.add(potentialBase);
            }
        }

        for (final Pair<Integer, String> potentialArgument : potentialArguments) {
            final int paramIndex = potentialArgument.getLeft();
            final String paramType = potentialArgument.getRight();
            // omit Object type
            if (paramType.equals("java.lang.Object")) {
                continue;
            }

            final List<String> parents = hierarchy.getParentClassReverse(paramType);
            final int low = hierarchy.getCurrentIndex();
            // the reduction group will be: base, derived1, derive2, currentCls...
            // non-exist `=>` Object
            for (final String parent : parents) {
                if (parent.equals("java.lang.Object")) {
                    continue;
                }
                final int idx = hierarchy.nextIndex();
                hierarchy.addReductionPoint(new ParamSubTypingRP(idx, paramIndex, parent));
            }
            final int idx = hierarchy.nextIndex();
            hierarchy.addReductionPoint(new ParamSubTypingRP(idx, paramIndex, paramType));

            final int high = hierarchy.getCurrentIndex();
            rpGroups.add(new RPGroup(low, high, paramIndex));
        }
    }

    public void analyzeCallDependency() {
        if (this.tryCatchBlocks.isEmpty() || options.doMethodWithTryCatch) {
            final DefaultDirectedGraph<InsnConsumer, DefaultEdge> dependency =
                    new DefaultDirectedGraph<>(DefaultEdge.class);

            // denotes the instruction node & (if method invocation) argument
            final Map<AbstractInsnNode, List<InsnConsumer>> methodMap = new HashMap<>();
            final Map<AbstractInsnNode, Set<InsnConsumer>> consumerMap = new HashMap<>();
            final Map<InsnConsumer, Set<SourceValue>> sources = new HashMap<>();

            // record source value dependency via stack analysis
            final SourceInterpreter interpreter = new SourceInterpreter(ASM9) {
                @Override
                public void popOperation(AbstractInsnNode insn, SourceValue v1, SourceValue v2) {
                    addAll(insn, Arrays.asList(v1, v2));
                    super.popOperation(insn, v1, v2);
                }

                @Override
                public void copyDependency(AbstractInsnNode insn, SourceValue v1, SourceValue v2, SourceValue v3, SourceValue v4) {
                    addAll(insn, Arrays.asList(v1, v2, v3, v4));
                    super.copyDependency(insn, v1, v2, v3, v4);
                }

                @Override
                public SourceValue unaryOperation(AbstractInsnNode insn, SourceValue value) {
                    addOne(insn, value);
                    return super.unaryOperation(insn, value);
                }

                @Override
                public SourceValue binaryOperation(AbstractInsnNode insn, SourceValue v1, SourceValue v2) {
                    addAll(insn, Arrays.asList(v1, v2));
                    return super.binaryOperation(insn, v1, v2);
                }

                @Override
                public SourceValue ternaryOperation(AbstractInsnNode insn, SourceValue v1, SourceValue v2, SourceValue v3) {
                    addAll(insn, Arrays.asList(v1, v2, v3));
                    return super.ternaryOperation(insn, v1, v2, v3);
                }

                @Override
                public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
                    // split argument consumers for methods
                    final int size = values.size();
                    if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
                        // static method with no argument
                        if (size != 0) {
                            for (int i = 0; i < size; ++i) {
                                addMethod(insn, values.get(i), i);
                            }
                        } else {
                            addMethod(insn, null, -1);
                        }
                    } else {
                        addAll(insn, values);
                    }
                    return super.naryOperation(insn, values);
                }

                private void addOne(AbstractInsnNode insn, SourceValue value) {
                    if (value != null) {
                        final InsnConsumer consumer = InsnConsumer.fromNormalInsn(insn);
                        consumerMap.computeIfAbsent(insn, x -> new HashSet<>()).add(consumer);
                        sources.computeIfAbsent(consumer, x -> new HashSet<>()).add(value);
                    }
                }

                private void addAll(AbstractInsnNode insn, List<? extends SourceValue> values) {
                    final InsnConsumer consumer = InsnConsumer.fromNormalInsn(insn);
                    consumerMap.computeIfAbsent(insn, x -> new HashSet<>()).add(consumer);
                    values = values.stream().filter(Objects::nonNull).collect(Collectors.toList());
                    sources.computeIfAbsent(consumer, x -> new HashSet<>()).addAll(values);
                }

                private void addMethod(AbstractInsnNode insn, SourceValue value, int argNum) {
                    final InsnConsumer consumer = InsnConsumer.fromMethodInsn(insn, argNum);
                    consumerMap.computeIfAbsent(insn, x -> new HashSet<>()).add(consumer);
                    methodMap.computeIfAbsent(insn, x -> new ArrayList<>()).add(consumer);
                    sources.computeIfAbsent(consumer, x -> new HashSet<>());
                    if (value != null) {
                        sources.get(consumer).add(value);
                    }
                }
            };

            // start analyzing
            final Analyzer<SourceValue> analyzer = new Analyzer<>(interpreter);
            try {
                analyzer.analyze(className, methodNode);
            } catch (AnalyzerException ex) {
                GlobalConfig.println("MethodAnalyzer error: " + ex);
            }

            // add appearing vertices
            sources.keySet().forEach(dependency::addVertex);

            // fill out empty consumers
            for (final AbstractInsnNode insn: methodNode.instructions) {
                // Ignore description instructions
                if (insn.getType() == AbstractInsnNode.FRAME
                        || insn.getType() == AbstractInsnNode.LABEL
                        || insn.getType() == AbstractInsnNode.LINE) {
                    continue;
                }
                if (!consumerMap.containsKey(insn)) {
                    final InsnConsumer consumer = InsnConsumer.fromNormalInsn(insn);
                    consumerMap.computeIfAbsent(insn, x -> new HashSet<>()).add(consumer);
                    dependency.addVertex(consumer);
                }
            }

            // add dependency edges
            for (final AbstractInsnNode insn: consumerMap.keySet()) {
                final Set<InsnConsumer> consumers = consumerMap.get(insn);
                for (final InsnConsumer consumer: consumers) {
                    if (!sources.containsKey(consumer)) {
                        continue;
                    }
                    for (final SourceValue sv: sources.get(consumer)) {
                        for (final AbstractInsnNode fromInsn: sv.insns) {
                            // Ignore self loops
                            if (fromInsn != insn) {
                                for (final InsnConsumer fromConsumer: consumerMap.get(fromInsn)) {
                                    dependency.addEdge(fromConsumer, consumer);
                                }
                            }
                        }
                    }
                }
            }

            this.insnMap = new UnionInsnMap(dependency, consumerMap, methodMap);
            this.insnMap.unionNaiveDependency();

            for (final AbstractInsnNode insnNode: methodMap.keySet()) {
                final MethodInsnNode methodNode = (MethodInsnNode) insnNode;
                if (!options.addInitMethodRemoval) {
                    if (methodNode.name.equals("<init>") || methodNode.name.equals("<clinit>")) {
                        continue;
                    }
                }
                final int idx = hierarchy.nextIndex();
                rpPoints.put(idx, insnNode);
                rpReversePoints.put(insnNode, idx);
                hierarchy.addReductionPoint(
                        new MethodNopRP(idx, this.instructions.indexOf(insnNode), insnNode));
            }
        } else {
            for (final AbstractInsnNode insnNode: this.instructions) {
                if (insnNode instanceof MethodInsnNode) {
                    final MethodInsnNode methodNode = (MethodInsnNode) insnNode;
                    if (!options.addInitMethodRemoval) {
                        if (methodNode.name.equals("<init>") || methodNode.name.equals("<clinit>")) {
                            continue;
                        }
                    }
                    final int idx = hierarchy.nextIndex();
                    rpPoints.put(idx, insnNode);
                    rpReversePoints.put(insnNode, idx);
                    hierarchy.addReductionPoint(
                            new MethodNopRP(idx, this.instructions.indexOf(insnNode), insnNode));
                }
            }
        }
    }

    public void analyze() {
        if (options.addParamSubtyping) {
            analyzeParameter();
        }
        if (options.addMethodRemoval) {
            analyzeCallDependency();
        }
    }

    public boolean accept(final ClassVisitor classVisitor, final MethodNode reader, final SortedSet<Integer> closure) {
        if (!options.addParamSubtyping || closure.size() == rpSection.size()) {
            final String[] exceptionsArray = reader.exceptions == null ? null : exceptions.toArray(new String[0]);
            final MethodVisitor methodVisitor =
                    classVisitor.visitMethod(
                            reader.access, reader.name, reader.desc, reader.signature, exceptionsArray);
            if (methodVisitor != null) {
                return accept(methodVisitor, reader, closure);
            }
            return true;
        }

        final String oldDescriptor = reader.desc;
        final String oldSignature = reader.signature;

        /*
        final List<ParamSubTypingRP> subTypingRps = new ArrayList<>();
        for (final RPGroup group: rpGroups) {
            final int rp = group.maxInRange(closure);
            if (rp == -1) {
                subTypingRps.add(new ParamSubTypingRP(0, group.attribute, "Object"));
            } else {
                subTypingRps.add((ParamSubTypingRP) hierarchy.getReductionPoint(rp));
            }
        }
        reader.desc = ASMUtils.rewriteDescriptor(this.desc, subTypingRps);
        reader.signature = ASMUtils.rewriteSignature(this.signature, subTypingRps);
         */

        final String[] exceptionsArray = reader.exceptions == null ? null : reader.exceptions.toArray(new String[0]);
        final MethodVisitor methodVisitor =
                classVisitor.visitMethod(
                        reader.access, reader.name, reader.desc, reader.signature, exceptionsArray);
        boolean valid = true;
        if (methodVisitor != null) {
            valid = accept(methodVisitor, reader, closure);
        }

        return valid;
    }

    public boolean accept(final MethodVisitor methodVisitor, final MethodNode reader, final SortedSet<Integer> closure) {
        if (!options.addMethodRemoval || closure.size() == rpSection.size()) {
            super.accept(methodVisitor);
            return true;
        }

        final InsnList insnList = this.instructions;

        if (this.tryCatchBlocks.isEmpty() || options.doMethodWithTryCatch) {
            final Set<AbstractInsnNode> removedMethodCalls = new HashSet<>();
            final List<MethodNopRP> methodNopRps = new ArrayList<>();

            for (final int rpt: rpPoints.keySet()) {
                if (!closure.contains(rpt)) {
                    final ReductionPoint rp = hierarchy.getReductionPoint(rpt);
                    rp.assignInsnNode(reader.instructions);
                    removedMethodCalls.add(rp.getInsnNode());
                    methodNopRps.add((MethodNopRP) rp);
                }
            }

            final UnionInsnMap insnCopy = new UnionInsnMap(insnMap);
            final Set<InsnGroup> removedGroups = insnCopy.getRemovalInsnGroup(removedMethodCalls);
            // insnCopy.printTo("graphs/" + this.name + ".dot");

            final Set<AbstractInsnNode> removedInsns = new HashSet<>();
            final Map<AbstractInsnNode, Set<Integer>> removedArgs = new HashMap<>();
            for (final InsnGroup group: removedGroups) {
                removedArgs.computeIfAbsent(group.consumerInsn.insn, x -> new TreeSet<>())
                        .add(group.consumerInsn.argNum);
                for (final InsnConsumer consumer: group.supplierInsns) {
                    if (consumer.insn != group.consumerInsn.insn) {
                        removedInsns.add(reader.instructions.get(insnList.indexOf(consumer.insn)));
                    }
                }
            }

            for (final AbstractInsnNode insn: removedInsns) {
                // reader.instructions.insert(insn, new InsnNode(Opcodes.NOP));
                reader.instructions.remove(insn);
            }

            final Set<Integer> actualRpts = new HashSet<>();
            for (final InsnGroup group: removedGroups) {
                final int rpt = rpReversePoints.get(group.consumerInsn.insn);
                actualRpts.add(rpt);
            }
            for (final int rpt: actualRpts) {
                final ReductionPoint rp = hierarchy.getReductionPoint(rpt);
                rp.applyReduction(reader.instructions, removedArgs.getOrDefault(
                        rpPoints.get(rpt), new TreeSet<>()));
            }
        } else {
            final List<MethodNopRP> methodNopRps = new ArrayList<>();

            for (final int rpt: rpPoints.keySet()) {
                if (!closure.contains(rpt)) {
                    final ReductionPoint rp = hierarchy.getReductionPoint(rpt);
                    rp.assignInsnNode(reader.instructions);
                    methodNopRps.add((MethodNopRP) rp);
                }
            }

            for (final MethodNopRP rp: methodNopRps) {
                rp.assignInsnNode(reader.instructions);
            }
            for (final MethodNopRP rp: methodNopRps) {
                rp.applyReduction(reader.instructions, new TreeSet<>());
            }
        }

        // ASMUtils.printInsnList(this.instructions);
        // System.out.println("---------");
        // ASMUtils.printInsnList(reader.instructions);

        reader.accept(methodVisitor);

        return true;
    }
}
