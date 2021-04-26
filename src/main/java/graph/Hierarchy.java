package graph;

import helper.ASMUtils;
import helper.JGraphTUtils;
import jvm.MethodAnalyzer;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import reduction.ReductionPoint;

import java.util.*;

/**
 * A global view of all class hierarchy & reduction points
 */
public class Hierarchy {
    EdgeReversedGraph<ClassVertex, DefaultEdge> reversedGraph;
    public final DirectedMultigraph<ClassVertex, DefaultEdge> graph;
    public final HashSet<String> classNames;
    public final HashMap<String, ClassVertex> vertices;
    public final HashMap<String, ClassVertex> interfaces;
    public final HashMap<String, List<String>> cacheParents;

    public final List<ReductionPoint> reductionPoints;
    public int reductionIndex;

    public final HashMap<ImmutableTriple<String, String, String>, String> computedDescriptor;

    public Hierarchy() {
        this.graph = new DirectedMultigraph<>(DefaultEdge.class);
        this.classNames = new HashSet<>();
        this.vertices = new HashMap<>();
        this.interfaces = new HashMap<>();
        this.cacheParents = new HashMap<>();
        this.reductionPoints = new ArrayList<>();
        this.reductionIndex = 0;
        this.computedDescriptor = new HashMap<>();
    }

    public int getCurrentIndex() {
        return reductionIndex;
    }

    public int nextIndex() {
        reductionIndex += 1;
        return reductionIndex - 1;
    }

    public void addComputedDesc(final MethodAnalyzer analyzer, final String desc) {
        this.computedDescriptor.put(ImmutableTriple.of(analyzer.className, analyzer.name, analyzer.desc), desc);
    }

    public void setComputedDesc(final MethodInsnNode insn) {
        final ImmutableTriple<String, String, String> triple = ImmutableTriple.of(insn.owner, insn.name, insn.desc);
        if (this.computedDescriptor.containsKey(triple)) {
            insn.desc = this.computedDescriptor.get(triple);
        } else {
            final List<String> parents = getParentClassReverse(insn.owner);
            for (final String parent: parents) {
                final ImmutableTriple<String, String, String> t = ImmutableTriple.of(parent, insn.name, insn.desc);
                if (this.computedDescriptor.containsKey(t)) {
                    insn.desc = this.computedDescriptor.get(t);
                }
            }
        }

    }

    public void clearComputedDesc() {
        this.computedDescriptor.clear();
    }

    public void addReductionPoint(final ReductionPoint rp) {
        reductionPoints.add(rp);
    }

    public ReductionPoint getReductionPoint(final int index) {
        return reductionPoints.get(index);
    }

    public void clearReductionPoint() {
        reductionIndex = 0;
        reductionPoints.clear();
    }

    public void addClass(final ClassVertex cv, final boolean inSrc) {
        if (inSrc) {
            classNames.add(cv.name);
        }
        graph.addVertex(cv);
        vertices.put(cv.name, cv);
        if (cv.isInterface) {
            interfaces.put(cv.name, cv);
        }
    }

    public void addEdges() {
        for (final ClassVertex cv: graph.vertexSet()) {
            if (vertices.containsKey(cv.superCls)) {
                graph.addEdge(vertices.get(cv.superCls), cv);
            }
            for (final String itf: cv.interfaces) {
                if (interfaces.containsKey(itf)) {
                    graph.addEdge(vertices.get(itf), cv);
                }
            }
        }
        reversedGraph = new EdgeReversedGraph<>(graph);
    }

    // return reversed parents, don't contain the requested type
    public List<String> getParentClassReverse(final String className) {
        if (cacheParents.containsKey(className)) {
            return cacheParents.get(className);
        }
        final ClassVertex cls = vertices.get(className);
        final List<String> parents = new ArrayList<>();
        if (cls == null) {
            cacheParents.put(className, parents);
            return parents;
        }
        // BFS all parent classes of the cls.
        final BreadthFirstIterator<ClassVertex, DefaultEdge> iter =
                new BreadthFirstIterator<>(reversedGraph, cls);
        ClassVertex lastCls = null;
        while (iter.hasNext()) {
            lastCls = iter.next();
            parents.add(lastCls.name);
        }
        // sometimes the lib is empty
        if (lastCls != null && !lastCls.superCls.equals("java/lang/Object")) {
            parents.add(lastCls.superCls);
        }
        Collections.reverse(parents);
        cacheParents.put(className, parents);
        return parents;
    }

    public String resolveFieldOwner(final FieldInsnNode insn) {
        if (!classNames.contains(insn.owner)) {
            return insn.owner;
        }
        final ClassVertex cv = resolveFieldOwner(vertices.get(insn.owner), insn.name);
        if (cv == null) {
            return insn.owner;
        }
        return cv.name;
    }

    public String resolveMethodOwner(final MethodInsnNode insn) {
        if (insn.itf) {
            return insn.owner;
        }
        if (!classNames.contains(insn.owner)) {
            return insn.owner;
        }
        final ClassVertex cv = resolveMethodOwner(vertices.get(insn.owner),
                ImmutablePair.of(insn.name, insn.desc));
        if (cv == null) {
            return insn.owner;
        }
        return cv.name;
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

    public ClassVertex resolveFieldOwner(final ClassVertex cls, final String name) {
        ClassVertex baseCls = cls;
        while (!baseCls.fields.containsKey(name)) {
            if (vertices.containsKey(baseCls.superCls)) {
                baseCls = vertices.get(baseCls.superCls);
            } else {
                return null;
            }
        }
        return baseCls;
    }
}
