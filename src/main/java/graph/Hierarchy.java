package graph;

import jvm.StubCallVisitor;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.*;

public class Hierarchy {
    public final DirectedMultigraph<ClassVertex, DefaultEdge> graph;
    public final HashMap<String, ClassVertex> vertices;
    public final HashMap<String, ClassVertex> interfaces;

    public final HashMap<CallInfo, InlineClassMethod> cachedCallInfo;
    public final HashSet<CallSite> callSites;
    public final HashSet<CallSite> stubCallSites;
    public final List<List<List<CallSite>>> poolCallSites;
    public final List<List<CallSite>> classCallSites;

    public List<List<CallSite>> currentClassCallSites;
    public List<CallSite> currentMethodCallSites;
    private int callCounter;
    private int callClsCounter;
    private int stubClsCounter;
    private int stubCounter;

    public Hierarchy() {
        this.graph = new DirectedMultigraph<>(DefaultEdge.class);
        this.vertices = new HashMap<>();
        this.interfaces = new HashMap<>();

        this.cachedCallInfo = new HashMap<>();
        this.callSites = new HashSet<>();
        this.stubCallSites = new HashSet<>();

        this.currentClassCallSites = new ArrayList<>();
        this.currentMethodCallSites = new ArrayList<>();
        this.poolCallSites = new ArrayList<>();
        this.classCallSites = new ArrayList<>();

        this.stubClsCounter = 0;
        this.stubCounter = 0;
        this.callClsCounter = 0;
        this.callCounter = 0;
    }

    public void addClass(final ClassVertex cv) {
        graph.addVertex(cv);
        vertices.put(cv.name, cv);
        if (cv.isInterface) {
            interfaces.put(cv.name, cv);
        }
    }

    public void addEdges() {
        for (final ClassVertex cv: graph.vertexSet()) {
            // System.out.println(cv.name + " <: " + cv.superCls);
            if (vertices.containsKey(cv.superCls)) {
                graph.addEdge(vertices.get(cv.superCls), cv);
            }
            for (final String itf: cv.interfaces) {
                // System.out.println(cv.name + " -> " + itf);
                if (interfaces.containsKey(itf)) {
                    graph.addEdge(vertices.get(itf), cv);
                }
            }
        }
    }

    public void visitEndMethod(final boolean isInitial) {
        if (isInitial) {
            if (!this.currentMethodCallSites.isEmpty()) {
                this.classCallSites.add(this.currentMethodCallSites);
                this.currentClassCallSites.add(this.currentMethodCallSites);
            }
            this.currentMethodCallSites = new ArrayList<>();
        }
    }

    public void visitEndClass(final boolean isInitial) {
        if (isInitial) {
            if (!this.currentClassCallSites.isEmpty()) {
                this.poolCallSites.add(this.currentClassCallSites);
            }
            this.currentClassCallSites = new ArrayList<>();
        }
        this.stubClsCounter = 0;
        this.callClsCounter = 0;
    }

    public void visitEndClassPool() {
        this.stubClsCounter = 0;
        this.stubCounter = 0;
        this.callClsCounter = 0;
        this.callCounter = 0;
    }

    public ClassVertex resolveMethodOwner(final ClassVertex cls, final ImmutablePair<String, String> nat) {
        ClassVertex baseCls = cls;
        while (!baseCls.methods.containsKey(nat)) {
            if (vertices.containsKey(baseCls.superCls)) {
                baseCls = vertices.get(baseCls.superCls);
            } else {
                return null;
            }
        }
        return baseCls;
    }

    public void resolveToNativeCall(final StubCallVisitor mv,
                                    final int opcode, final String owner, final String name, final String desc,
                                    final boolean initial) {
        if (initial)
            cachedCallInfo.put(new CallInfo(opcode, owner, name, desc, null), null);
        mv.visitMethodInsnNative(opcode, owner, name, desc, opcode == Opcodes.INVOKEINTERFACE);
    }

    public void resolveToStubCall(final StubCallVisitor mv, final InlineClassMethod stub,
                                  final int opcode, final String owner, final String name, final String desc,
                                  final boolean initial) {
        if (initial) {
            final CallInfo callInfo = new CallInfo(opcode, owner, name, desc, stub);
            cachedCallInfo.put(callInfo, stub);
            final CallSite callSite = new CallSite(mv.clsName, this.stubClsCounter, this.stubCounter, callInfo);
            stubCallSites.add(callSite);
        }
        addStubCounter();
        stub.generateInlineStub(mv);
    }

    public void resolveToInlineCall(final StubCallVisitor mv,
                                    final int opcode, final String owner, final String name, final String desc,
                                    final boolean initial) {
        if (initial) {
            final CallInfo callInfo = new CallInfo(opcode, owner, name, desc, null);
            final CallSite callSite = new CallSite(mv.clsName, this.callClsCounter, this.callCounter, callInfo);
            callSites.add(callSite);
            currentMethodCallSites.add(callSite);
        }
        addCallCounter();
        generateInlineReplacement(mv, opcode, owner, name, desc, opcode == Opcodes.INVOKEINTERFACE);
    }

    private void addStubCounter() {
        this.stubClsCounter += 1;
        this.stubCounter += 1;
    }

    private void addCallCounter() {
        this.callCounter += 1;
        this.callClsCounter += 1;
    }

    public void generateInlineReplacement(final StubCallVisitor mv,
                                          int opcode, String _owner, String _name, String desc, boolean _itf) {
        final boolean isStatic = opcode == Opcodes.INVOKESTATIC;
        final Type returnType = Type.getReturnType(desc);
        final List<Type> revArgTypes = Arrays.asList(Type.getArgumentTypes(desc));
        Collections.reverse(revArgTypes);

        // pop out arguments
        for (final Type ty: revArgTypes) {
            final int size = ty.getSize();
            if (size == 2) {
                mv.visitInsn(Opcodes.POP2);
            } else if (size == 1) {
                mv.visitInsn(Opcodes.POP);
            }
        }
        // pop out this reference
        if (!isStatic) {
            mv.visitInsn(Opcodes.POP);
        }
        // add inline values
        switch (returnType.getSort()) {
            case Type.VOID: break;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                mv.visitInsn(Opcodes.ICONST_0);
                break;
            case Type.LONG:
                mv.visitInsn(Opcodes.LCONST_0);
                break;
            case Type.FLOAT:
                mv.visitInsn(Opcodes.FCONST_0);
                break;
            case Type.DOUBLE:
                mv.visitInsn(Opcodes.DCONST_0);
                break;
            default:
                mv.visitInsn(Opcodes.ACONST_NULL);
                break;
        }
    }

    public void resolveAllCall(final StubCallVisitor mv, final HashSet<Integer> closure,
                               final int opcode, final String owner, final String name, final String desc,
                               final boolean initial) {
        if (!initial && closure.contains(this.callCounter)) {
            // non-initial run && preserved => just resolve to normal call
            resolveToNativeCall(mv, opcode, owner, name, desc, false);
            addCallCounter();
        } else {
            // otherwise, just replace to pop-load instructions
            resolveToInlineCall(mv, opcode, owner, name, desc, initial);
        }
    }

    public void resolveStubCall(final StubCallVisitor mv, final HashSet<Integer> closure,
                                final int opcode, final String owner, final String name, final String desc,
                                final boolean initial) {
        final CallInfo callInfo = new CallInfo(opcode, owner, name, desc, null);
        if (cachedCallInfo.containsKey(callInfo)) {
            final InlineClassMethod stub = cachedCallInfo.get(callInfo);
            if (stub == null) {
                // non-stubbed cache call-info
                resolveToNativeCall(mv, opcode, owner, name, desc, initial);
            } else {
                if (!initial && closure.contains(this.stubCounter)) {
                    // current reduction requires it to exist
                    resolveToNativeCall(mv, opcode, owner, name, desc, false);
                    // add missing counter
                    addStubCounter();
                } else {
                    // stubbed call
                    resolveToStubCall(mv, stub, opcode, owner, name, desc, initial);
                }
            }
            return;
        }

        final ClassVertex cls = vertices.get(owner);
        final ImmutablePair<String, String> nat = ImmutablePair.of(name, desc);

        // library classes
        if (cls == null) {
            resolveToNativeCall(mv, opcode, owner, name, desc, initial);
            return;
        }

        if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE) {
            // for virtual invocation, ensure all subclasses of the owner has the method stubbed
            // for interface methods, ensure all classes implement the interface has the method stubbed

            final ClassVertex resolveCls = resolveMethodOwner(cls, nat);
            // test if the resolved method is stubbed or abstract
            if (resolveCls == null || resolveCls.isMethodNotStubbedOrAbstract(nat)) {
                resolveToNativeCall(mv, opcode, owner, name, desc, initial);
                return;
            }
            InlineClassMethod stubMethod = null;
            if (resolveCls.isMethodNotAbstract(nat)) {
                stubMethod = resolveCls.stubMethods.get(nat);
            }

            // BFS all subclasses of owner cls.
            final BreadthFirstIterator<ClassVertex, DefaultEdge> iter = new BreadthFirstIterator<>(graph, cls);
            while (iter.hasNext()) {
                final ClassVertex childCls = iter.next();
                if (childCls.methods.containsKey(nat) && childCls.isMethodNotStubbedOrAbstract(nat)) {
                    resolveToNativeCall(mv, opcode, owner, name, desc, initial);
                    return;
                } else {
                    if (stubMethod == null && childCls.methods.containsKey(nat) && childCls.isMethodNotAbstract(nat)) {
                        stubMethod = childCls.stubMethods.get(nat);
                    }
                }
            }

            // now generate the inline stubs (could be a abstract method not implemented by anyone)
            if (stubMethod == null) {
                resolveToNativeCall(mv, opcode, owner, name, desc, initial);
            } else {
                resolveToStubCall(mv, stubMethod, opcode, owner, name, desc, initial);
            }
        } else if (opcode == Opcodes.INVOKESPECIAL) {
            // for special invocations (super, private, <init>)

            // <init>s are ignored

            // private method can be safely removed (judge by owner)
            if (owner.equals(mv.clsName)) {
                final ClassVertex resolveCls = resolveMethodOwner(cls, nat);
                // test if the owner method is stubbed or abstract
                if (resolveCls.isMethodNotStubbedOrAbstract(nat)) {
                    resolveToNativeCall(mv, opcode, owner, name, desc, initial);
                    return;
                }
                resolveToStubCall(mv, resolveCls.stubMethods.get(nat), opcode, owner, name, desc, initial);
                return;
            }

            // super method (judge parent method is stubbed)
            final ClassVertex baseCls = resolveMethodOwner(vertices.get(owner), nat);
            if (baseCls == null || baseCls.isMethodNotStubbedOrAbstract(nat)) {
                resolveToNativeCall(mv, opcode, owner, name, desc, initial);
                return;
            }
            resolveToStubCall(mv, baseCls.stubMethods.get(nat), opcode, owner, name, desc, initial);
        } else if (opcode == Opcodes.INVOKESTATIC) {
            // for static methods, ensure the owner/name/desc tuple is matched

            // test if the owner method is stubbed (static dispatched)
            if (cls.isMethodNotStubbedOrAbstract(nat)) {
                resolveToNativeCall(mv, opcode, owner, name, desc, initial);
                return;
            }
            resolveToStubCall(mv, cls.stubMethods.get(nat), opcode, owner, name, desc, initial);
        } else {
            resolveToNativeCall(mv, opcode, owner, name, desc, initial);
        }
    }
}
