package reduction;

import graph.CallSite;
import graph.Hierarchy;
import jvm.ClassPool;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WorkingEnv {
    public final boolean hierarchyReduce = false;
    public final boolean isReplaceAll = true;
    public final String name;
    public final String decompiler;
    public HashSet<Integer> progressions;

    public WorkingEnv(final String name, final String decompiler) {
        this.name = name;
        this.decompiler = decompiler;
    }

    public void setTemp() throws IOException {
        final String origPath = "/Users/liranxiao/result/full/"
                + name
                + "/"
                + decompiler
                + "/items+logic/reduced";
        final String origLibPath = "/Users/liranxiao/result/full/"
                + name
                + "/"
                + decompiler
                + "/items+logic/benchmark/lib";
        final String origExpectationPath = "/Users/liranxiao/result/full/"
                + name
                + "/"
                + decompiler
                + "/items+logic/expectation";
        final String origSrcPath = "/Users/liranxiao/result/full/"
                + name
                + "/"
                + decompiler
                + "/items+logic/workfolder/final/sandbox/" + decompiler + "/src";
        final String origPredicatePath = "/Users/liranxiao/garbage/temp/predicate_" + decompiler + ".sh";
        final String origCompilePath = "/Users/liranxiao/garbage/temp/compile.sh";

        final String tmpPath = "/Users/liranxiao/garbage/tmp/";
        FileUtils.deleteDirectory(new File(tmpPath));
        final String bytePath = "/Users/liranxiao/garbage/tmp/classes";
        FileUtils.copyDirectory(new File(origPath), new File(bytePath));
        final String srcPath = "/Users/liranxiao/garbage/tmp/orig/src";
        FileUtils.copyDirectory(new File(origSrcPath), new File(srcPath));
        final String libPath = "/Users/liranxiao/garbage/tmp/lib";
        FileUtils.copyDirectory(new File(origLibPath), new File(libPath));
        final String expectationPath = "/Users/liranxiao/garbage/tmp/expectation";
        FileUtils.copyFile(new File(origExpectationPath), new File(expectationPath));

        final Set<PosixFilePermission> perms = Files.getPosixFilePermissions(Paths.get(origCompilePath));

        final String predicatePath = "/Users/liranxiao/garbage/tmp/predicate_" + decompiler + ".sh";
        FileUtils.copyFile(new File(origPredicatePath), new File(predicatePath));
        Files.setPosixFilePermissions(Paths.get(predicatePath), perms);

        final String compilePath = "/Users/liranxiao/garbage/tmp/compile.sh";
        FileUtils.copyFile(new File(origCompilePath), new File(compilePath));
        Files.setPosixFilePermissions(Paths.get(compilePath), perms);

        final String outputPath = "/Users/liranxiao/garbage/tmp/reduced/";
        final String decompilerPath = "/Users/liranxiao/garbage/tmp/" + decompiler;
        FileUtils.deleteDirectory(new File(outputPath));
        FileUtils.deleteDirectory(new File(decompilerPath));
    }

    public void removeFull() throws IOException {
        final String origPath = "/Users/liranxiao/result/full/"
                + name
                + "/"
                + decompiler
                + "/items+logic/reduced_extra";
        FileUtils.deleteDirectory(new File(origPath));
    }

    public void moveToFull() throws IOException {
        final String outputPath = "/Users/liranxiao/garbage/tmp/reduced/";
        final String origPath = "/Users/liranxiao/result/full/"
                + name
                + "/"
                + decompiler
                + "/items+logic/reduced_extra";
        FileUtils.copyDirectory(new File(outputPath), new File(origPath));
    }

    public boolean runIdentity() throws IOException, InterruptedException {
        final String bytePath = "/Users/liranxiao/garbage/tmp/classes/";
        final String libPath = "/Users/liranxiao/garbage/tmp/lib/";
        final String expectationPath = "/Users/liranxiao/garbage/tmp/expectation";
        final String compilePath = "/Users/liranxiao/garbage/tmp/compile.sh";
        final String predicatePath = "/Users/liranxiao/garbage/tmp/predicate_" + decompiler + ".sh";
        final String outputPath = "/Users/liranxiao/garbage/tmp/reduced/";

        final Hierarchy hierarchy = new Hierarchy();
        final ClassPool pool = new ClassPool(bytePath, libPath, outputPath, isReplaceAll);

        pool.readLibs(hierarchy);
        pool.readClasses(hierarchy);

        pool.identityWriteClasses(hierarchy);

        final Predicate predicate = new Predicate(
                predicatePath,
                compilePath,
                outputPath,
                libPath,
                expectationPath);

        return predicate.runPredicate();
    }

    public String runReduction() throws IOException, InterruptedException {
        final String bytePath = "/Users/liranxiao/garbage/tmp/classes/";
        final String libPath = "/Users/liranxiao/garbage/tmp/lib/";
        final String expectationPath = "/Users/liranxiao/garbage/tmp/expectation";
        final String compilePath = "/Users/liranxiao/garbage/tmp/compile.sh";
        final String predicatePath = "/Users/liranxiao/garbage/tmp/predicate_" + decompiler + ".sh";
        final String outputPath = "/Users/liranxiao/garbage/tmp/reduced/";

        final Hierarchy hierarchy = new Hierarchy();
        final ClassPool pool = new ClassPool(bytePath, libPath, outputPath, isReplaceAll);

        // read lib & classes
        pool.readLibs(hierarchy);
        pool.readClasses(hierarchy);

        // post-compute the edges
        hierarchy.addEdges();

        // write initial run & collect info
        pool.writeClasses(hierarchy, true, new HashSet<>());

        final Predicate predicate = new Predicate(
                predicatePath,
                compilePath,
                outputPath,
                libPath,
                expectationPath);

        if (hierarchyReduce) {
            final Pair<HashSet<List<List<CallSite>>>, Boolean> clsResult = runReductionClass(hierarchy, pool, predicate);
            System.out.println("Class   Level => (" + clsResult.getRight() + ") "
                    + clsResult.getLeft().size() + "/" + hierarchy.poolCallSites.size());

            final List<List<CallSite>> mthdCards = clsResult.getLeft().stream()
                    .flatMap(List::stream).collect(Collectors.toList());

            final Pair<HashSet<List<CallSite>>, Boolean> mthdResult = runReductionMethod(
                    mthdCards, hierarchy, pool, predicate);
            System.out.println("Method  Level => (" + mthdResult.getRight() + ") "
                    + mthdResult.getLeft().size() + "/" + mthdCards.size());

            final List<CallSite> cards = mthdResult.getLeft().stream()
                    .flatMap(List::stream)
                    .sorted(Comparator.comparing((cs) -> cs.poolIndex))
                    .collect(Collectors.toList());

            final Pair<HashSet<CallSite>, Boolean> elemResult = runReductionElement(
                    cards, hierarchy, pool, predicate);
            System.out.println("Element Level => (" + elemResult.getRight() + ") "
                    + elemResult.getLeft().size() + "/" + hierarchy.callSites.size());

            if (!elemResult.getRight()) {
                if (!mthdResult.getRight()) {
                    progressions = BinaryPolicy.BLOCK2_AGGREGATOR.apply(clsResult.getLeft());
                    pool.writeClasses(hierarchy, false, progressions);
                    return clsResult.getLeft().size() + "/" + hierarchy.callSites.size();
                } else {
                    progressions = BinaryPolicy.BLOCK_AGGREGATOR.apply(mthdResult.getLeft());
                    pool.writeClasses(hierarchy, false, progressions);
                    return mthdResult.getLeft().size() + "/" + hierarchy.callSites.size();
                }
            }
            progressions = BinaryPolicy.DEFAULT_AGGREGATOR.apply(elemResult.getLeft());
            return elemResult.getLeft().size() + "/" + hierarchy.callSites.size();
        } else {
            final List<CallSite> cards = hierarchy.callSites.stream()
                    .sorted(Comparator.comparing((cs) -> cs.poolIndex))
                    .collect(Collectors.toList());

            final Pair<HashSet<CallSite>, Boolean> elemResult = runReductionElement(
                    cards, hierarchy, pool, predicate);
            System.out.println("Element Level => (" + elemResult.getRight() + ") "
                    + elemResult.getLeft().size() + "/" + hierarchy.callSites.size());
            progressions = BinaryPolicy.DEFAULT_AGGREGATOR.apply(elemResult.getLeft());
            return elemResult.getLeft().size() + "/" + hierarchy.callSites.size();
        }
    }

    private Pair<HashSet<List<List<CallSite>>>, Boolean> runReductionClass(
            final Hierarchy hierarchy,
            final ClassPool pool, final Predicate predicate)
            throws IOException, InterruptedException {
        final BinaryPolicy<List<List<CallSite>>> policy = new BinaryPolicy<>(
                hierarchy, hierarchy.poolCallSites, BinaryPolicy.BLOCK2_AGGREGATOR);
        policy.runReduction(pool, predicate);
        final boolean isValidFinal = policy.runFinal(pool, predicate);
        return ImmutablePair.of(policy.getProgressions(), isValidFinal);
    }

    private Pair<HashSet<List<CallSite>>, Boolean> runReductionMethod(
            final List<List<CallSite>> elements,
            final Hierarchy hierarchy,
            final ClassPool pool, final Predicate predicate)
            throws IOException, InterruptedException {
        final BinaryPolicy<List<CallSite>> policy = new BinaryPolicy<>(
                hierarchy, elements, BinaryPolicy.BLOCK_AGGREGATOR);
        policy.runReduction(pool, predicate);
        final boolean isValidFinal = policy.runFinal(pool, predicate);
        return ImmutablePair.of(policy.getProgressions(), isValidFinal);
    }

    private Pair<HashSet<CallSite>, Boolean> runReductionElement(
            final List<CallSite> elements,
            final Hierarchy hierarchy,
            final ClassPool pool, final Predicate predicate)
            throws IOException, InterruptedException {
        final BinaryPolicy<CallSite> policy = new BinaryPolicy<>(
                hierarchy, elements, BinaryPolicy.DEFAULT_AGGREGATOR);
        policy.runReduction(pool, predicate);
        final boolean isValidFinal = policy.runFinal(pool, predicate);
        return ImmutablePair.of(policy.getProgressions(), isValidFinal);
    }
}
