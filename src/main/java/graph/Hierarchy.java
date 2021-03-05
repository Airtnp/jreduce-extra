package graph;

import helper.ASMUtils;
import helper.JGraphTUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import reduction.ReductionPoint;

import java.util.*;

/**
 * A global view of all class hierarchy & reduction points
 */
public class Hierarchy {
    EdgeReversedGraph<ClassVertex, DefaultEdge> reversedGraph;
    public final DirectedMultigraph<ClassVertex, DefaultEdge> graph;
    public final HashMap<String, ClassVertex> vertices;
    public final HashMap<String, ClassVertex> interfaces;
    public final HashMap<String, List<String>> cacheParents;

    public final List<ReductionPoint> reductionPoints;
    public int reductionIndex;

    public Hierarchy() {
        this.graph = new DirectedMultigraph<>(DefaultEdge.class);
        this.vertices = new HashMap<>();
        this.interfaces = new HashMap<>();
        this.cacheParents = new HashMap<>();
        this.reductionPoints = new ArrayList<>();
        this.reductionIndex = 0;
    }

    public int getCurrentIndex() {
        return reductionIndex;
    }

    public int nextIndex() {
        reductionIndex += 1;
        return reductionIndex - 1;
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

    public void addClass(final ClassVertex cv) {
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
        while (iter.hasNext()) {
            final ClassVertex childCls = iter.next();
            parents.add(childCls.name);
        }
        Collections.reverse(parents);
        cacheParents.put(className, parents);
        return parents;
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
}
