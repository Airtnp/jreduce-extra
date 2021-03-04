package reduction;

import graph.Hierarchy;
import helper.GlobalConfig;
import jvm.ClassPool;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BinaryPolicy<T extends Comparable<? super T>> {
    private final Hierarchy hierarchy;

    private SortedSet<T> progressions;
    private List<T> sortedSpace;
    public final int callSiteSize;

    private final Function<SortedSet<T>, SortedSet<Integer>> aggregator;

    private int retryCounter;
    private SortedSet<T> lastValidAttempt;

    static final Function<SortedSet<Integer>, SortedSet<Integer>> ID_AGGREGATOR = (x) -> x;

    static final Function<SortedSet<ReductionPoint>, SortedSet<Integer>> DEFAULT_AGGREGATOR = (x) ->
            x.stream()
                    .map((rp) -> rp.globalIndex)
                    .collect(Collectors.toCollection(TreeSet::new));

    static final Function<SortedSet<List<ReductionPoint>>, SortedSet<Integer>> BLOCK_AGGREGATOR = (x) ->
            x.stream()
                    .flatMap(List::stream)
                    .map((rp) -> rp.globalIndex)
                    .collect(Collectors.toCollection(TreeSet::new));

    static final Function<SortedSet<List<List<ReductionPoint>>>, SortedSet<Integer>> BLOCK2_AGGREGATOR = (x) ->
            x.stream()
                    .flatMap(List::stream)
                    .flatMap(List::stream)
                    .map((rp) -> rp.globalIndex)
                    .collect(Collectors.toCollection(TreeSet::new));

    public BinaryPolicy(final Hierarchy hierarchy, final List<T> sortedSpace,
                        final Function<SortedSet<T>, SortedSet<Integer>> aggregator) {
        this.hierarchy = hierarchy;
        this.progressions = new TreeSet<>();
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
                if (GlobalConfig.debug)
                    GlobalConfig.println("\t" + retryCounter + "-th element: " + r);
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
        if (GlobalConfig.debug) {
            GlobalConfig.println("\tCurrent progress: " + progressions);
        }

        // if discard all sort space and we still preserve the compiler error, just return
        if (runPredicate(progressions, classPool, predicate)) {
            return -1;
        }

        // binary search of the first element necessary for the compiler error
        int l = 0, r = sortedSpace.size();
        while (r > l) {
            final SortedSet<T> currentClosure = new TreeSet<>(progressions);
            final int mid = l + (r - l) / 2;
            currentClosure.addAll(sortedSpace.subList(0, mid + 1));
            if (GlobalConfig.debug)
                GlobalConfig.println("\tCurrent closure: " + Arrays.toString(new int[]{l, mid + 1, r}));
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
        if (GlobalConfig.debug)
            GlobalConfig.println("\tCurrent element: " + r);
        return r;
    }

    // Return if the compiler error is preserved
    public boolean runPredicate(final SortedSet<T> currentClosure,
                                final ClassPool classPool,
                                final Predicate predicate) throws IOException, InterruptedException {
        final boolean valid = classPool.writeClasses(this.hierarchy, aggregator.apply(currentClosure), false);
        if (!valid) {
            return false;
        }

        if (predicate.runPredicate()) {
            lastValidAttempt = currentClosure;
            return true;
        } else {
            return false;
        }
    }

    public boolean runFinal(final ClassPool classPool, final Predicate predicate) throws IOException, InterruptedException {
        final boolean valid = classPool.writeClasses(this.hierarchy, aggregator.apply(progressions), false);
        if (!valid) {
            return false;
        }

        if (predicate.runPredicate()) {
            return true;
        } else if (lastValidAttempt != null) {
            progressions = lastValidAttempt;
            return true;
        } else {
            return false;
        }
    }

    public SortedSet<T> getProgressions() {
        return progressions;
    }
}
