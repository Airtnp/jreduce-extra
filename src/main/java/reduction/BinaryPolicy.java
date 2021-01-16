package reduction;

import graph.CallSite;
import graph.Hierarchy;
import jvm.ClassPool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BinaryPolicy<T> {
    private final Hierarchy hierarchy;
    private HashSet<T> progressions;
    private List<T> sortedSpace;
    private final Function<HashSet<T>, HashSet<Integer>> aggregator;
    private int retryCounter;
    private HashSet<T> lastValidAttempt;
    public final int callSiteSize;

    static final Function<HashSet<CallSite>, HashSet<Integer>> DEFAULT_AGGREGATOR = (x) ->
            x.stream()
                    .map((cs) -> cs.poolIndex)
                    .collect(Collectors.toCollection(HashSet::new));

    static final Function<HashSet<List<CallSite>>, HashSet<Integer>> BLOCK_AGGREGATOR = (x) ->
            x.stream()
                    .flatMap(List::stream)
                    .map((cs) -> cs.poolIndex)
                    .collect(Collectors.toCollection(HashSet::new));

    static final Function<HashSet<List<List<CallSite>>>, HashSet<Integer>> BLOCK2_AGGREGATOR = (x) ->
            x.stream()
                    .flatMap(List::stream)
                    .flatMap(List::stream)
                    .map((cs) -> cs.poolIndex)
                    .collect(Collectors.toCollection(HashSet::new));

    public BinaryPolicy(final Hierarchy hierarchy, final List<T> sortedSpace,
                        final Function<HashSet<T>, HashSet<Integer>> aggregator) {
        this.hierarchy = hierarchy;
        this.progressions = new HashSet<>();
        this.sortedSpace = sortedSpace;
        this.aggregator = aggregator;
        this.callSiteSize = sortedSpace.size();
        this.lastValidAttempt = null;
        this.retryCounter = 0;
    }

    public void runReduction(final ClassPool classPool,
                            final Predicate predicate) throws IOException, InterruptedException {
        while (!sortedSpace.isEmpty() && retryCounter < 500) {
            final int r = runProgression(classPool, predicate);
            if (r != -1) {
                System.out.println("\t" + retryCounter + "-th element: " + r);
                progressions.add(sortedSpace.get(r));
                sortedSpace = new ArrayList<>(sortedSpace.subList(0, r));
            } else {
                sortedSpace.clear();
            }
            retryCounter += 1;
        }
    }

    public int runProgression(final ClassPool classPool,
                              final Predicate predicate) throws IOException, InterruptedException {
        // @DEBUG
        // System.out.println("\tCurrent progress: " + progressions);

        // if discard all sort space and we still preserve the compiler error, just return
        if (runPredicate(progressions, classPool, predicate)) {
            return -1;
        }

        // binary search of the first element necessary for the compiler error
        int l = 0, r = sortedSpace.size();
        while (r > l) {
            final HashSet<T> currentClosure = new HashSet<>(progressions);
            final int mid = l + (r - l) / 2;
            currentClosure.addAll(sortedSpace.subList(0, mid + 1));
            // @DEBUG
            // System.out.println("\tCurrent closure: " + Arrays.toString(new int[]{l, mid + 1, r}));
            if (runPredicate(currentClosure, classPool, predicate)) {
                r = mid;
            } else {
                l = mid + 1;
            }

            // an approximate on stepping out nearby elements
            if (retryCounter >= 25 && r == sortedSpace.size() && r - l < sortedSpace.size() / 100) {
                for (int i = l; i < r; ++i) {
                    progressions.add(sortedSpace.get(i));
                }
                break;
            }
        }

        if (r == sortedSpace.size()) {
            r -= 1;
        }
        // @DEBUG
        // System.out.println("\tCurrent element: " + r);
        return r;
    }

    // Return if the compiler error is preserved
    public boolean runPredicate(final HashSet<T> currentClosure,
                                final ClassPool classPool,
                                final Predicate predicate) throws IOException, InterruptedException {
        classPool.writeClasses(this.hierarchy, false, aggregator.apply(currentClosure));
        if (predicate.runPredicate()) {
            // @DEBUG
            // System.out.println("\t\t=> Given: " + aggregator.apply(currentClosure));
            // System.out.println("\t\t=> Passed");
            lastValidAttempt = currentClosure;
            return true;
        } else {
            // @DEBUG
            // System.out.println("\t\t=> Given: " + aggregator.apply(currentClosure));
            // System.out.println("\t\t=> Failed");
            return false;
        }
    }

    public boolean runFinal(final ClassPool classPool, final Predicate predicate) throws IOException, InterruptedException {
        classPool.writeClasses(this.hierarchy, false, aggregator.apply(progressions));
        if (predicate.runPredicate()) {
            return true;
        } else if (lastValidAttempt != null) {
            progressions = lastValidAttempt;
            return true;
        } else {
            return false;
        }
    }

    public HashSet<T> getProgressions() {
        return progressions;
    }
}
