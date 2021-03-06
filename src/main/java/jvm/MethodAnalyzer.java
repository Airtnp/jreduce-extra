package jvm;

import graph.Hierarchy;
import graph.InlineClassMethod;
import asm.tree.analysis.SourceInterpreter;
import asm.tree.analysis.Analyzer;
import helper.ASMUtils;
import helper.GlobalConfig;
import org.objectweb.asm.tree.TypeInsnNode;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.SourceValue;

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
import sun.reflect.generics.tree.*;

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
    public final Map<Integer, String> baseParents;

    public Set<String> classTypeConstraint;

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
        this.baseParents = new TreeMap<>();
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
        final boolean isStatic = (this.access & Opcodes.ACC_STATIC) != 0;
        final boolean isGeneric = methodNode.signature != null;
        // Parse signature for potential reduced arguments
        final Type[] args = Type.getArgumentTypes(methodNode.desc);
        final String signature = isGeneric ? methodNode.signature : methodNode.desc;
        final MethodTypeSignature sig = SignatureParser.make().parseMethodSig(signature);
        final int synthetic_offset = args.length - sig.getParameterTypes().length;
        // XXX: Assume all type invariant here
        // Therefore, only consider raw Object types with their parents
        // ClassTypeSignature: Objects
        // TypeVariableSignature: Generic types
        // ArrayTypeSignature: Array types
        // BaseType: own signatures
        final List<Pair<Integer, String>> potentialArguments = new ArrayList<>();
        final Set<Integer> argCandidates = new HashSet<>();
        final Map<Integer, Integer> argOffsetMap = new HashMap<>();
        int index = (isStatic ? 0 : 1) + synthetic_offset;
        int paramCount = synthetic_offset;
        if (!isStatic) {
            argCandidates.add(0);
        }
        for (final TypeSignature ty: sig.getParameterTypes()) {
            if (ty instanceof ClassTypeSignature) {
                final ClassTypeSignature cty = (ClassTypeSignature)ty;
                // Don't consider inner inner classes (A.B.C...) / generic classes
                if (cty.getPath().size() == 1 && cty.getPath().get(0).getTypeArguments().length == 0) {
                    final String className = cty.getPath().get(0).getName().replace('.', '/');
                    if (hierarchy.classNames.contains(className)) {
                        potentialArguments.add(ImmutablePair.of(index, className));
                        argCandidates.add(index);
                        argOffsetMap.put(index, paramCount);
                    }
                }
            }
            paramCount += 1;
            if (ty instanceof LongSignature || ty instanceof DoubleSignature) {
                index += 2;
            } else {
                index += 1;
            }
        }

        final Map<Integer, Set<String>> argConstraints = new HashMap<>();
        Map<AbstractInsnNode, Set<Integer>> sourceArgs = new HashMap<>();

        final SourceInterpreter tainter = new SourceInterpreter(ASM9) {
            @Override
            public void copyDependency(AbstractInsnNode insn, SourceValue v1, SourceValue v2, SourceValue v3, SourceValue v4) {
                // XXX: maybe I need a second-pass or fixed point for this dependency
                List<SourceValue> values = Arrays.asList(v1, v2, v3, v4);
                Set<Integer> dependency = new HashSet<>();
                for (final SourceValue sv: values) {
                    if (sv == null) {
                        continue;
                    }
                    for (final AbstractInsnNode sinsn: sv.insns) {
                        if (sourceArgs.containsKey(sinsn)) {
                            dependency.addAll(sourceArgs.get(sinsn));
                        }
                    }
                }
                if (!dependency.isEmpty()) {
                    sourceArgs.computeIfAbsent(insn, x -> new HashSet<>())
                            .addAll(dependency);
                }
            }

            @Override
            public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) {
                if (insn.getOpcode() == Opcodes.ALOAD) {
                    final int var = ((VarInsnNode) insn).var;
                    if (argCandidates.contains(var)) {
                        sourceArgs.computeIfAbsent(insn, x -> new HashSet<>()).add(var);
                    }
                }
                return super.copyOperation(insn, value);
            }
        };

        final Analyzer<SourceValue> taint_analyzer = new Analyzer<>(tainter);
        try {
            taint_analyzer.analyze(className, methodNode);
        } catch (AnalyzerException ex) {
            GlobalConfig.println("MethodAnalyzer error: " + ex);
        }

        // only care about specific instruction affects Object types & this
        // ALOAD
        // PUTSTATIC/GETFIELD/PUTFIELD/INVOKE/CHECKCAST/INSTANCEOF
        // XXX: an exception is AASTORE, otherwise should taint multinewarray instructions
        final SourceInterpreter interpreter = new SourceInterpreter(ASM9) {
            @Override
            public SourceValue unaryOperation(AbstractInsnNode insn, SourceValue value) {
                // PUTSTATIC/GETFIELD
                if (insn.getOpcode() == Opcodes.GETFIELD) {
                    final FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
                    addDependency(value, resolveFieldOwner(fieldInsnNode));
                }
                if (insn.getOpcode() == Opcodes.PUTSTATIC) {
                    final FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
                    addDependency(value, Type.getType(fieldInsnNode.desc).getInternalName());
                }
                if (insn.getOpcode() == Opcodes.CHECKCAST || insn.getOpcode() == Opcodes.INSTANCEOF) {
                    addDependency(value, "DYNAMIC");
                }
                return super.unaryOperation(insn, value);
            }

            @Override
            public SourceValue binaryOperation(AbstractInsnNode insn, SourceValue v1, SourceValue v2) {
                // PUTFIELD
                if (insn.getOpcode() == Opcodes.PUTFIELD) {
                    final FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
                    addDependency(v1, resolveFieldOwner(fieldInsnNode));
                    addDependency(v2, Type.getType(fieldInsnNode.desc).getInternalName());
                }
                return super.binaryOperation(insn, v1, v2);
            }

            @Override
            public SourceValue ternaryOperation(AbstractInsnNode insn, SourceValue v1, SourceValue v2, SourceValue v3) {
                // AASTORE
                if (insn.getOpcode() == Opcodes.AASTORE) {
                    // don't assume array type
                    addDependency(v3, "DYNAMIC");
                }
                return super.ternaryOperation(insn, v1, v2, v3);
            }

            @Override
            public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
                // INVOKE
                if (insn.getOpcode() == Opcodes.INVOKEDYNAMIC) {
                    final InvokeDynamicInsnNode methodInsnNode = (InvokeDynamicInsnNode) insn;
                    final Type[] argTypes = Type.getArgumentTypes(methodInsnNode.desc);
                    for (int idx = 0; idx < values.size(); idx += 1) {
                        final SourceValue sv = values.get(idx);
                        if (sv == null) {
                            continue;
                        }
                        addDependency(sv, argTypes[idx].getInternalName());
                    }
                }
                else if (insn.getOpcode() != Opcodes.MULTIANEWARRAY) {
                    final MethodInsnNode methodInsnNode = (MethodInsnNode) insn;
                    final Type[] argTypes = Type.getArgumentTypes(methodInsnNode.desc);
                    int offset = 0;
                    if (insn.getOpcode() != Opcodes.INVOKESTATIC && insn.getOpcode() != Opcodes.INVOKEDYNAMIC) {
                        offset = 1;
                        addDependency(values.get(0), resolveMethodOwner(methodInsnNode));
                    }
                    for (int idx = offset; idx < values.size(); idx += 1) {
                        final SourceValue sv = values.get(idx);
                        if (sv == null) {
                            continue;
                        }
                        addDependency(sv, argTypes[idx - offset].getInternalName());
                    }
                }
                return super.naryOperation(insn, values);
            }

            public void addDependency(SourceValue sv, String name) {
                for (final AbstractInsnNode src: sv.insns) {
                    if (sourceArgs.containsKey(src)) {
                        for (final int k: sourceArgs.get(src)) {
                            argConstraints.computeIfAbsent(k, x -> new HashSet<>())
                                    .add(name);
                        }
                    }
                }
            }

            public String resolveFieldOwner(FieldInsnNode insn) {
                return hierarchy.resolveFieldOwner(insn);
            }

            public String resolveMethodOwner(MethodInsnNode insn) {
                return hierarchy.resolveMethodOwner(insn);
            }
        };

        // start analyzing
        final Analyzer<SourceValue> analyzer = new Analyzer<>(interpreter);
        try {
            analyzer.analyze(className, methodNode);
        } catch (AnalyzerException ex) {
            GlobalConfig.println("MethodAnalyzer error: " + ex);
        }

        // @DEBUG
        /*
        if (className.contains("VersionConfirmDialog") && name.equals("<init>")) {
            System.out.println("Debug");
        }
         */

        // don't reduce enum.valueOf
        if (!name.equals("valueOf") && !name.contains("access$")) {

            for (final Pair<Integer, String> potentialArgument : potentialArguments) {
                int paramIndex = potentialArgument.getLeft();
                final String paramType = potentialArgument.getRight();
                // omit Object type
                if (paramType.equals("java/lang/Object")) {
                    continue;
                }
                List<String> parents = hierarchy.getParentClassReverse(paramType);

                String baseParent = "java/lang/Object";
                if (argConstraints.containsKey(paramIndex)) {
                    final Set<String> constraints = argConstraints.get(paramIndex);
                    // if requires current type
                    if (constraints.contains(paramType) || constraints.contains("DYNAMIC")) {
                        continue;
                    }
                    for (int i = parents.size() - 1; i >= 0; i--) {
                        if (constraints.contains(parents.get(i))) {
                            baseParent = parents.get(i);
                            parents = parents.subList(i, parents.size());
                            break;
                        }
                    }
                }

                final int realParamIndex = argOffsetMap.get(paramIndex);
                baseParents.put(realParamIndex, baseParent);
                final int low = hierarchy.getCurrentIndex();
                // the reduction group will be: base, derived1, derive2, currentCls...
                // non-exist `=>` baseParent
                for (final String parent : parents) {
                    if (parent.equals(baseParent) || parent.equals(paramType)) {
                        continue;
                    }
                    final int idx = hierarchy.nextIndex();
                    hierarchy.addReductionPoint(new ParamSubTypingRP(idx, realParamIndex, parent));
                }
                final int idx = hierarchy.nextIndex();
                hierarchy.addReductionPoint(new ParamSubTypingRP(idx, realParamIndex, paramType));

                final int high = hierarchy.getCurrentIndex();
                rpGroups.add(new RPGroup(low, high, realParamIndex));
            }
        }

        if (!isStatic) {
            this.classTypeConstraint = argConstraints.getOrDefault(0, new HashSet<>());
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

    public void compute_descriptor(final SortedSet<Integer> closure) {
        final List<ParamSubTypingRP> subTypingRps = new ArrayList<>();
        for (final RPGroup group: rpGroups) {
            final int rp = group.maxInRange(closure);
            if (rp == -1) {
                subTypingRps.add(new ParamSubTypingRP(0, group.attribute, baseParents.get(group.attribute)));
            } else {
                subTypingRps.add((ParamSubTypingRP) hierarchy.getReductionPoint(rp));
            }
        }
        final String desc = ASMUtils.rewriteDescriptor(this.desc, subTypingRps);
        hierarchy.addComputedDesc(this, desc);
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

        final List<ParamSubTypingRP> subTypingRps = new ArrayList<>();
        for (final RPGroup group: rpGroups) {
            final int rp = group.maxInRange(closure);
            if (rp == -1) {
                subTypingRps.add(new ParamSubTypingRP(0, group.attribute, baseParents.get(group.attribute)));
            } else {
                subTypingRps.add((ParamSubTypingRP) hierarchy.getReductionPoint(rp));
            }
        }
        reader.desc = ASMUtils.rewriteDescriptor(reader.desc, subTypingRps);
        reader.signature = ASMUtils.rewriteSignature(reader.signature, subTypingRps);

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
            if (options.addParamSubtyping) {
                for (final AbstractInsnNode insn: reader.instructions) {
                    if (insn instanceof MethodInsnNode) {
                        hierarchy.setComputedDesc((MethodInsnNode) insn);
                    }
                }
            }
            reader.accept(methodVisitor);
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

        reader.accept(methodVisitor);

        return true;
    }
}
