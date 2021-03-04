package jvm;

import helper.JGraphTUtils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/* Basically based on jgrapht.alg.util.UnionFind<T>
   But controls the group direction
 */
public class UnionInsnMap {
    private final Graph<InsnConsumer, DefaultEdge> dependency;
    private final Map<InsnConsumer, InsnConsumer> parentMap;
    private Map<InsnConsumer, Set<InsnConsumer>> nonUnionReverseEdge;
    private final Map<AbstractInsnNode, Set<InsnConsumer>> consumerMap;
    private final Map<AbstractInsnNode, List<InsnConsumer>> methodMap;
    private int count; // number of components

    public UnionInsnMap(
            final Graph<InsnConsumer, DefaultEdge> dependency,
            final Map<AbstractInsnNode, Set<InsnConsumer>> consumerMap,
            final Map<AbstractInsnNode, List<InsnConsumer>> methodMap) {
        this.parentMap = new LinkedHashMap<>();
        this.nonUnionReverseEdge = new HashMap<>();
        this.consumerMap = consumerMap;
        this.methodMap = methodMap;
        this.dependency = dependency;
        for (final InsnConsumer consumer : dependency.vertexSet()) {
            parentMap.put(consumer, consumer);
        }
        count = dependency.vertexSet().size();
    }

    public UnionInsnMap(final UnionInsnMap insnMap) {
        this.parentMap = new HashMap<>(insnMap.parentMap);
        this.nonUnionReverseEdge = new HashMap<>();
        for (final InsnConsumer consumer: insnMap.nonUnionReverseEdge.keySet()) {
            this.nonUnionReverseEdge.put(consumer, new HashSet<>(insnMap.nonUnionReverseEdge.get(consumer)));
        }
        this.consumerMap = insnMap.consumerMap;
        this.methodMap = insnMap.methodMap;
        this.dependency = insnMap.dependency;
        this.count = insnMap.count;
    }

    public void unionRemovalDependency(final Set<InsnConsumer> removedMethodArgs) {
        for (final InsnConsumer source: removedMethodArgs) {
            final Set<DefaultEdge> outEdges = dependency.outgoingEdgesOf(source);
            if (outEdges.size() == 1) {
                final DefaultEdge edge = outEdges.iterator().next();
                final InsnConsumer target = dependency.getEdgeTarget(edge);
                // only remove consecutive method call dependency
                if (target.isMethod() && removedMethodArgs.contains(target)) {
                    this.union(source, target);
                    nonUnionReverseEdge.get(target).remove(source);
                }
            }
        }
        this.flattenParent();
    }

    public Set<InsnGroup> getRemovalInsnGroup(final Set<AbstractInsnNode> removedMethodCalls) {
        // To solve the problem of keeping incoming edges will keep much bytecode between consecutive method calls
        // merge method calls into single one only if both should be removed
        // M1 #0...#n -> M2 #i:
        //  both kept: no change
        //  M2 removed: must keep the instructions for handling M1 (different arguments may intervene)
        //  M1 removed: just remove M1 groups
        //  both removed: could merge into a single group
        final Set<InsnGroup> removalInsns = new HashSet<>();
        final Set<InsnConsumer> removedMethodArgs = removedMethodCalls.stream()
                .flatMap(c -> consumerMap.get(c).stream())
                .collect(Collectors.toSet());

        this.unionRemovalDependency(removedMethodArgs);
        final Map<InsnConsumer, InsnGroup> groupMap = getGroupMap();

        for (final InsnConsumer source: removedMethodArgs) {
            // if an argument group is merged into another group
            if (!groupMap.containsKey(source)) {
                continue;
            }
            // only remove a group if no incoming edge towards it
            if (!nonUnionReverseEdge.containsKey(source)
                    || nonUnionReverseEdge.get(source).isEmpty()) {
                removalInsns.add(groupMap.get(source));
            }
        }

        return removalInsns;
    }

    public int getNumOfSets() {
        return count;
    }

    public void unionNaiveDependency() {
        for (final DefaultEdge edge: dependency.edgeSet()) {
            final InsnConsumer source = dependency.getEdgeSource(edge);
            final InsnConsumer target = dependency.getEdgeTarget(edge);
            // fuse single-output & non-method nodes
            if (dependency.outDegreeOf(source) == 1 && !source.isMethod()) {
                this.union(source, target);
            } else {
                this.nonUnionReverseEdge.computeIfAbsent(target, x -> new HashSet<>()).add(source);
            }
        }
        this.flattenParent();
    }

    public void flattenParent() {
        for (final InsnConsumer consumer: dependency.vertexSet()) {
            parentMap.put(consumer, find(consumer, null));
        }
        final Map<InsnConsumer, Set<InsnConsumer>> nonUnionReverseEdgeNew = new HashMap<>();
        for (final InsnConsumer consumer: nonUnionReverseEdge.keySet()) {
            nonUnionReverseEdgeNew.computeIfAbsent(parentMap.get(consumer), x -> new HashSet<>());
            for (final InsnConsumer source: nonUnionReverseEdge.get(consumer)) {
                nonUnionReverseEdgeNew.get(parentMap.get(consumer)).add(parentMap.get(source));
            }
        }
        nonUnionReverseEdge = nonUnionReverseEdgeNew;
    }

    public Map<InsnConsumer, InsnConsumer> getParentMap() {
        return parentMap;
    }

    public Map<InsnConsumer, Set<InsnConsumer>> getReverseParentMap() {
        final Map<InsnConsumer, Set<InsnConsumer>> reverseParentMap =
                new HashMap<>();

        for (final InsnConsumer insn: parentMap.keySet()) {
            final InsnConsumer parent = parentMap.get(insn);
            reverseParentMap.computeIfAbsent(parent, x -> new HashSet<>()).add(insn);
        }

        return reverseParentMap;
    }

    public Map<InsnConsumer, InsnGroup> getGroupMap() {
        final Map<InsnConsumer, Set<InsnConsumer>> reverseParentMap = getReverseParentMap();
        final Map<InsnConsumer, InsnGroup> groupMap = new HashMap<>();
        for (final InsnConsumer consumer: reverseParentMap.keySet()) {
            final InsnGroup insnGroup = new InsnGroup(consumer, reverseParentMap.get(consumer));
            groupMap.put(consumer, insnGroup);
        }
        this.flattenParent();
        return groupMap;
    }


    public InsnConsumer find(final InsnConsumer insn, final InsnConsumer parentDest) {
        if (!parentMap.containsKey(insn)) {
            throw new IllegalArgumentException(
                    "instruction is not contained in this UnionInsnMap data structure: " + insn);
        }

        InsnConsumer current = insn;
        if (parentDest == null) {
            while (true) {
                InsnConsumer parent = parentMap.get(current);
                if (parent.equals(current)) {
                    break;
                }
                current = parent;
            }
            final InsnConsumer root = current;

            // normal path compression
            current = insn;
            while (!current.equals(root)) {
                InsnConsumer parent = parentMap.get(current);
                parentMap.put(current, root);
                current = parent;
            }

            return root;
        } else {
            while (true) {
                // direct path compression
                parentMap.put(current, parentDest);
                InsnConsumer parent = parentMap.get(current);
                if (parent.equals(current)) {
                    break;
                }
                current = parent;
            }
            return current;
        }
    }

    public void union(InsnConsumer insnSrc, InsnConsumer insnDest) {
        if (!parentMap.containsKey(insnSrc) || !parentMap.containsKey(insnDest)) {
            throw new IllegalArgumentException("instructions must be contained in given set");
        }

        final InsnConsumer parentDest = find(insnDest, null);
        final InsnConsumer parentSrc = find(insnSrc, parentDest);

        // check if the elements are already in the same set
        if (parentSrc.equals(parentDest)) {
            return;
        }

        // discard ranking
        parentMap.put(parentSrc, parentDest);
        count--;
    }


    public DefaultDirectedGraph<InsnGroup, DefaultEdge> toGraph() {
        final DefaultDirectedGraph<InsnGroup, DefaultEdge> graph =
                new DefaultDirectedGraph<>(DefaultEdge.class);
        final Map<InsnConsumer, InsnGroup> groupMap = getGroupMap();

        groupMap.values().forEach(graph::addVertex);

        // Enhance: could do a second round, since some edges might not be fused A -> (B, C) -> D
        for (final InsnConsumer target: nonUnionReverseEdge.keySet()) {
            for (final InsnConsumer source: nonUnionReverseEdge.get(target)) {
                // fuse single-output & non-method nodes
                graph.addEdge(
                        groupMap.get(find(source, null)),
                        groupMap.get(find(target, null)));
            }
        }
        return graph;
    }

    public void printTo(final String path) {
        final DefaultDirectedGraph<InsnGroup, DefaultEdge> graph = toGraph();
        try {
            JGraphTUtils.printGraph(graph, path);
        } catch (IOException ignored) {}
    }
}
