package reduction;

import graph.Hierarchy;
import helper.GlobalConfig;
import jvm.ClassAnalyzeOptions;
import jvm.ClassAnalyzer;
import jvm.ClassPool;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

public class WorkingEnv {
    public final Path workingFolder;
    public final Path staticPredicatePath;
    public final Path staticCompilePath;
    public final String srcFolder;
    public final String targetFolder;

    public boolean finalValid = false;
    public String finalProgressions;

    public static boolean startFromTmp = false;
    public static boolean doClassCollapse = false;
    public Path currentTargetPath;

    public final String decompiler;

    public WorkingEnv(final Path workingFolder, final String srcFolder, final String targetFolder,
                      final Path staticPredicatePath, final Path staticCompilePath, final String decompiler) {
        this.workingFolder = workingFolder;
        this.srcFolder = srcFolder;
        this.targetFolder = targetFolder;
        this.staticPredicatePath = staticPredicatePath;
        this.staticCompilePath = staticCompilePath;
        this.decompiler = decompiler;
    }

    public WorkingEnv(final String name, final String decompiler) {
        this(jreduceFolder(name, decompiler), "reduced", "reduced_extra",
                staticPredicatePath(decompiler), staticCompilePath(), decompiler);
    }

    public WorkingEnv(final String name, final String decompiler,
                      final String srcFolder, final String targetFolder) {
        this(jreduceFolder(name, decompiler), srcFolder, targetFolder,
                staticPredicatePath(decompiler), staticCompilePath(), decompiler);
    }

    public static Path jreduceFolder(final String name, final String decompiler) {
        return Paths.get("/Users/liranxiao/result/full/"
                + name
                + "/"
                + decompiler
                + "/items+logic/");
    }

    public static Path staticPredicatePath(final String decompiler) {
        return Paths.get("/Users/liranxiao/garbage/temp/predicate_" + decompiler + ".sh");
    }

    public static Path staticCompilePath() {
        return Paths.get("/Users/liranxiao/garbage/temp/compile.sh");
    }

    public Path libPath() {
        return workingFolder.resolve("benchmark").resolve("lib");
    }

    public Path expectationPath() {
        return workingFolder.resolve("expectation");
    }

    public Path targetPredicatePath() {
        return workingFolder.resolve("predicate_extra.sh");
    }

    public Path targetCompilePath() {
        return workingFolder.resolve("compile_extra.sh");
    }

    public Path srcPredicatePath() {
        return staticPredicatePath;
    }

    public Path srcCompilePath() {
        return staticCompilePath;
    }

    public Path srcPath() {
        return workingFolder.resolve(srcFolder);
    }

    public Path targetPath() {
        return workingFolder.resolve(targetFolder);
    }

    public Path tmpPath() { return workingFolder.resolve("tmp"); }

    public Path currentTargetPath() {
        return currentTargetPath;
    }

    public void setTemp() throws IOException {
        final Set<PosixFilePermission> perms = Files.getPosixFilePermissions(staticPredicatePath);

        FileUtils.copyFile(srcPredicatePath().toFile(), targetPredicatePath().toFile());
        Files.setPosixFilePermissions(targetPredicatePath(), perms);

        FileUtils.copyFile(srcCompilePath().toFile(), targetCompilePath().toFile());
        Files.setPosixFilePermissions(targetCompilePath(), perms);
    }

    public void removeOldArtifacts() throws IOException {
        if (!startFromTmp) {
            FileUtils.deleteDirectory(tmpPath().toFile());
        }
        FileUtils.deleteDirectory(targetPath().toFile());
        FileUtils.deleteDirectory(workingFolder.resolve(decompiler).toFile());
    }

    public void runSingle(final Path path) throws IOException, InterruptedException {
        final Hierarchy hierarchy = new Hierarchy();
        final ClassAnalyzeOptions options = new ClassAnalyzeOptions();
        final ClassPool pool = new ClassPool(srcPath(), libPath(), targetPath());

        pool.readLibs(hierarchy);
        pool.readClassesSingle(path, hierarchy, options);

        final List<Integer> list = new ArrayList<>();
        for (int i = 0; i < hierarchy.reductionPoints.size(); ++i) {
            list.add(i);
        }
        final Predicate predicate = new Predicate(this, decompiler, GlobalConfig.debugPredicateDiff);
        final Pair<Set<Integer>, Boolean> result = runReductionElement(list, hierarchy, pool, predicate);
        System.out.println(result);
    }

    public boolean runIdentity() throws IOException, InterruptedException {
        final Hierarchy hierarchy = new Hierarchy();
        final ClassAnalyzeOptions options = new ClassAnalyzeOptions();
        options.addMethodRemoval = false;
        Path source;
        if (!startFromTmp) {
            source = srcPath();
        } else {
            source = tmpPath();
        }
        final ClassPool pool = new ClassPool(source, libPath(), targetPath());
        currentTargetPath = targetPath();

        pool.readLibs(hierarchy);
        pool.readClasses(hierarchy, options);

        pool.identityWriteClasses(hierarchy);

        final Predicate predicate = new Predicate(this, decompiler, GlobalConfig.debugPredicateDiff);

        return predicate.runPredicate();
    }

    public String runReduction() throws IOException, InterruptedException {
        final Hierarchy hierarchy = new Hierarchy();

        Pair<Set<Integer>, Boolean> result1;
        Path target = targetPath();
        if (doClassCollapse) {
            target = tmpPath();
        }
        if (!startFromTmp) {
            result1 = runReductionMethod(hierarchy, srcPath(), target);
        } else {
            result1 = doNotRunReduction();
        }
        final String p1 = result1.getLeft().size() + "/" + hierarchy.reductionPoints.size();

        if (!result1.getRight()) {
            finalProgressions = "(" + result1.getLeft() + " | " + "invalid" + ")";
            finalValid = false;
            return "(" + p1 + ", " + "invalid)";
        }

        hierarchy.clearReductionPoint();

        Pair<Set<Integer>, Boolean> result2;
        if (doClassCollapse) {
            result2 = runReductionClass(hierarchy, tmpPath(), targetPath());
        } else {
            result2 = doNotRunReduction();
        }
        final String p2 = result2.getLeft().size() + "/" + hierarchy.reductionPoints.size();

        finalProgressions = "(" + result1.getLeft() + " | " + result2.getLeft() + ")";
        finalValid = result1.getRight() & result2.getRight();

        return "(" + p1 + ", " + p2 + ")";
    }

    private Pair<Set<Integer>, Boolean> runReductionMethod(
            final Hierarchy hierarchy, final Path source, final Path target)
            throws IOException, InterruptedException {
        final ClassAnalyzeOptions options = new ClassAnalyzeOptions();
        final ClassPool pool = new ClassPool(source, libPath(), target);
        currentTargetPath = target;

        // read lib & classes
        pool.readLibs(hierarchy);
        pool.readClasses(hierarchy, options);

        // post-compute the edges
        hierarchy.addEdges();

        final Predicate predicate = new Predicate(this, decompiler, GlobalConfig.debugPredicateDiff);

        final List<Integer> list = new ArrayList<>();
        for (int i = 0; i < hierarchy.reductionPoints.size(); ++i) {
            list.add(i);
        }

        return runReductionElement(list, hierarchy, pool, predicate);
    }

    private Pair<Set<Integer>, Boolean> runReductionParam(
            final Hierarchy hierarchy, final Path source, final Path target)
            throws IOException, InterruptedException {
        final ClassAnalyzeOptions options = new ClassAnalyzeOptions();
        options.addHierarchy = false;
        options.addMethodRemoval = false;
        options.addParamSubtyping = true;
        final ClassPool pool = new ClassPool(source, libPath(), target);
        currentTargetPath = target;

        // read classes only
        pool.readClasses(hierarchy, options);

        final Predicate predicate = new Predicate(this, decompiler, GlobalConfig.debugPredicateDiff);

        final List<Integer> list = new ArrayList<>();
        for (int i = 0; i < hierarchy.reductionPoints.size(); ++i) {
            list.add(i);
        }

        return runReductionElement(list, hierarchy, pool, predicate);
    }

    private Pair<Set<Integer>, Boolean> runReductionClass(
            final Hierarchy hierarchy, final Path source, final Path target)
            throws IOException, InterruptedException {
        final ClassAnalyzeOptions options = new ClassAnalyzeOptions();
        options.addHierarchy = false;
        options.addMethodRemoval = false;
        options.addInitMethodRemoval = false;
        options.addParentCollapsing = true;
        final ClassPool pool = new ClassPool(source, libPath(), target);
        currentTargetPath = target;

        if (startFromTmp) {
            final ClassAnalyzeOptions optionRead = new ClassAnalyzeOptions();
            optionRead.addMethodRemoval = false;
            optionRead.addInitMethodRemoval = false;
            pool.readClasses(hierarchy, optionRead);
            hierarchy.addEdges();
        }

        // read classes only
        pool.readClasses(hierarchy, options);

        final Predicate predicate = new Predicate(this, decompiler, false);

        final List<Integer> list = new ArrayList<>();
        for (int i = 0; i < hierarchy.reductionPoints.size(); ++i) {
            list.add(i);
        }

        return runReductionElement(list, hierarchy, pool, predicate);
    }

    private Pair<Set<Integer>, Boolean> runReductionElement(
            final List<Integer> elements,
            final Hierarchy hierarchy,
            final ClassPool pool, final Predicate predicate)
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

    private Pair<Set<Integer>, Boolean> doNotRunReduction() {
        return ImmutablePair.of(new TreeSet<>(), true);
    }
}
