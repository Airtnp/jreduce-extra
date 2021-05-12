package reduction;

import graph.Hierarchy;
import helper.GlobalConfig;
import jvm.ClassAnalyzeOptions;
import jvm.ClassPool;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GeneralWorkingEnv {
    final Path srcPath;
    final Path targetPath;
    final Path libPath;
    final Path predicatePath;
    final Path workingFolder;
    final int option;

    public static int methodRemoval = 0;
    public static int classCollapse = 1;

    public GeneralWorkingEnv(Path srcPath, Path libPath, Path predicatePath, Path targetPath, Path workingFolder, int option) {
        this.srcPath = srcPath;
        this.targetPath = targetPath;
        this.libPath = libPath;
        this.predicatePath = predicatePath;
        this.workingFolder = workingFolder;
        this.option = option;
    }

    public boolean runIdentity() throws IOException, InterruptedException {
        final Hierarchy hierarchy = new Hierarchy();
        final ClassAnalyzeOptions options = new ClassAnalyzeOptions();
        options.addMethodRemoval = false;
        final ClassPool pool = new ClassPool(srcPath, libPath, targetPath);

        pool.readLibs(hierarchy);
        pool.readClasses(hierarchy, options);

        pool.identityWriteClasses(hierarchy);

        final GeneralPredicate predicate = new GeneralPredicate(workingFolder, predicatePath);

        return predicate.runPredicate();
    }

    public Pair<Set<Integer>, Boolean> runReduction() throws IOException, InterruptedException {
        final Hierarchy hierarchy = new Hierarchy();

        Pair<Set<Integer>, Boolean> result;

        if (this.option == JReduceWorkingEnv.methodRemoval) {
            result = runReductionMethod(hierarchy, srcPath, targetPath);
        } else {
            result = runReductionClass(hierarchy, srcPath, targetPath);
        }

        return result;
    }

    private Pair<Set<Integer>, Boolean> runReductionMethod(
            final Hierarchy hierarchy, final Path source, final Path target)
            throws IOException, InterruptedException {
        final ClassAnalyzeOptions options = new ClassAnalyzeOptions();
        final ClassPool pool = new ClassPool(source, libPath, target);

        // read lib & classes
        pool.readLibs(hierarchy);
        pool.readClasses(hierarchy, options);

        // post-compute the edges
        hierarchy.addEdges();

        final GeneralPredicate predicate = new GeneralPredicate(workingFolder, predicatePath);

        final List<Integer> list = new ArrayList<>();
        for (int i = 0; i < hierarchy.reductionPoints.size(); ++i) {
            list.add(i);
        }

        return runReductionElement(list, hierarchy, pool, predicate);
    }

    private Pair<Set<Integer>, Boolean> runReductionClass(
            final Hierarchy hierarchy, final Path source, final Path target)
            throws IOException, InterruptedException {

        final ClassAnalyzeOptions startOption = new ClassAnalyzeOptions();
        final ClassPool pool = new ClassPool(source, libPath, target);

        startOption.doReduction = false;
        startOption.checkClassAdapter = false;
        startOption.addMethodRemoval = false;
        pool.readLibs(hierarchy);
        pool.readClasses(hierarchy, startOption);
        hierarchy.addEdges();

        final ClassAnalyzeOptions options = new ClassAnalyzeOptions();
        options.addHierarchy = false;
        options.addMethodRemoval = false;
        options.addInitMethodRemoval = false;
        options.doMethodWithTryCatch = false;

        // FIXME: this is broken since we need all constraints from all methods
        // options.addParentCollapsing = true;
        options.addParamSubtyping = true;

        // read classes again
        pool.readClasses(hierarchy, options);

        final GeneralPredicate predicate = new GeneralPredicate(workingFolder, predicatePath);

        final List<Integer> list = new ArrayList<>();
        for (int i = 0; i < hierarchy.reductionPoints.size(); ++i) {
            list.add(i);
        }

        return runReductionElement(list, hierarchy, pool, predicate);
    }

    private Pair<Set<Integer>, Boolean> runReductionElement(
            final List<Integer> elements,
            final Hierarchy hierarchy,
            final ClassPool pool, final GeneralPredicate predicate)
            throws IOException, InterruptedException {
        final BinaryPolicy<Integer> policy = new BinaryPolicy<>(
                hierarchy, elements, BinaryPolicy.ID_AGGREGATOR);
        policy.runReduction(pool, predicate);

        final boolean isValidFinal = policy.runFinal(pool, predicate);
        if (GlobalConfig.debug) {
            GlobalConfig.println("Element Level => (" + isValidFinal + ") "
                    + policy.getProgressions().size() + "/" + hierarchy.reductionPoints.size());
        }

        return ImmutablePair.of(policy.getProgressions(), isValidFinal);
    }
}
