import graph.Hierarchy;
import helper.DebugClass;
import jvm.ClassAnalyzeOptions;
import jvm.ClassAnalyzer;
import org.apache.commons.cli.*;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;
import reduction.GeneralWorkingEnv;
import reduction.JReduceWorkingEnv;
import soot.G;

import java.io.*;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

import static org.objectweb.asm.Opcodes.ASM9;

public class Main {

    public static class ReductionTask implements Callable<Void> {
        final String name;
        final String decompiler;
        final ReentrantLock lock;
        final CSVPrinter printer;

        public ReductionTask(String name, String decompiler, ReentrantLock lock, CSVPrinter printer) {
            this.name = name;
            this.decompiler = decompiler;
            this.lock = lock;
            this.printer = printer;
        }

        @Override
        public Void call() {
            final JReduceWorkingEnv env = new JReduceWorkingEnv(name, decompiler,
                    "reduced2", "reduced2_cls", JReduceWorkingEnv.classCollapse);
            try {
                System.out.println(name + " - " + decompiler);
                env.setTemp();
                env.removeOldArtifacts();

                // Test if the bug is ASM-preserving
                final boolean isAsmPreserved = env.runIdentity();
                // Do reduction only if ASM preserved
                if (!isAsmPreserved) {
                    writeRecord(name, decompiler,
                            "", false, "ASMError", "", 0);
                }

                long startTime = System.currentTimeMillis();
                final String ratio = env.runReduction();
                long endTime = System.currentTimeMillis();
                writeRecord(name, decompiler,
                        ratio, env.finalValid, "success", env.finalProgressions, endTime - startTime);
            } catch (final FileNotFoundException | NoSuchFileException ignored) {

            } catch (final Exception ex) {
                writeRecord(name, decompiler,
                        "", false, ex.toString(), "" , 0);
            }
            return null;
        }

        private void writeRecord(String name, String decompiler, String ratio, boolean valid, String success, String finalProgressions, long time) {
            lock.lock();
            try {
                printer.printRecord(name, decompiler, "items+logic+reduced", ratio, valid, success, finalProgressions, time);
                printer.flush();
            } catch (Exception ignored) {

            } finally {
                lock.unlock();
            }
        }
    }

    public static void runJReduceAll() throws IOException, InterruptedException {
        // final ExecutorService pool = Executors.newWorkStealingPool(1);
        // final ReentrantLock lock = new ReentrantLock();
        final FileWriter fw = new FileWriter("logs/hierarchy_log_ch.csv", true);
        final CSVPrinter printer = new CSVPrinter(fw, CSVFormat.EXCEL);

        printer.printRecord("name", "predicate", "strategy", "ratio", "asm", "status", "progression", "time");
        printer.flush();

        final String[] decompilers = {"cfr", "fernflower", "procyon"};
        final File root = new File("/Users/liranxiao/result/full/");
        // final List<ReductionTask> tasks = new ArrayList<>();

        for (final File f: Objects.requireNonNull(root.listFiles())) {
            if (f.isDirectory() && f.getName().startsWith("url")) {
                final String name = f.getName();
                for (final String decompiler: decompilers) {
                    // final ReductionTask task = new ReductionTask(name, decompiler, lock, printer);
                    // tasks.add(task);
                    final JReduceWorkingEnv env = new JReduceWorkingEnv(name, decompiler,
                        "reduced2_mthdrm", "reduced2_cls", JReduceWorkingEnv.classCollapse);
                    try {
                        System.out.println(name + " - " + decompiler);
                        env.setTemp();
                        env.removeOldArtifacts();

                        // Test if the bug is ASM-preserving
                        final boolean isAsmPreserved = env.runIdentity();
                        // Do reduction only if ASM preserved
                        if (!isAsmPreserved) {
                            printer.printRecord(name, decompiler, "items+logic+reduced",
                                    "", false, "ASMError", "", 0);
                        }

                        long startTime = System.currentTimeMillis();
                        final String ratio = env.runReduction();
                        long endTime = System.currentTimeMillis();
                        printer.printRecord(name, decompiler, "items+logic+reduced",
                                ratio, env.finalValid, "success", env.finalProgressions, endTime - startTime);
                    } catch (final FileNotFoundException | NoSuchFileException ignored) {

                    } catch (final Exception ex) {
                        printer.printRecord(name, decompiler, "items+logic+reduced",
                                "", false, ex.toString(), "" , 0);
                    }
                    printer.flush();
                }
            }
        }
        // pool.invokeAll(tasks);
        // pool.shutdown();

        fw.close();
    }

    public static void runJReduceMulti(final List<ImmutablePair<String, String>> validR) throws IOException, InterruptedException {
        final FileWriter f = new FileWriter("logs/hierarchy_individual_log.csv", true);
        final CSVPrinter printer = new CSVPrinter(f, CSVFormat.EXCEL);
        printer.printRecord("name", "predicate", "strategy", "ratio", "asm", "status", "progression");

        for (final ImmutablePair<String, String> p: validR) {
            System.out.println(p.left + " - " + p.right);
            try {
                final JReduceWorkingEnv env = new JReduceWorkingEnv(p.left, p.right,
                    "reduced2_mthdrm", "reduced2_cls", JReduceWorkingEnv.classCollapse);
                env.setTemp();
                env.removeOldArtifacts();

                // Test if the bug is ASM-preserving
                final boolean isAsmPreserved = env.runIdentity();
                // Do reduction only if ASM preserved
                if (isAsmPreserved) {
                    long startTime = System.currentTimeMillis();
                    final String ratio = env.runReduction();
                    long endTime = System.currentTimeMillis();
                    printer.printRecord(
                            p.left, p.right, "items+logic+reduced",
                            ratio, true, "success", env.finalProgressions, endTime - startTime);
                } else {
                    printer.printRecord(
                            p.left, p.right, "items+logic+reduced",
                            "", false, "asm-halt", "", 0);
                }
            } catch (final Exception ex) {
                printer.printRecord(
                        p.left, p.right, "items+logic+reduced",
                        "", "", ex, "", 0);
                throw ex;
            }
            f.flush();
        }

        f.close();
    }

    public static void runJReduceSingle(final ImmutablePair<String, String> p, final Path path) throws IOException, InterruptedException {
        System.out.println(p.left + " - " + p.right + " - " + path);
        final JReduceWorkingEnv env = new JReduceWorkingEnv(
            p.left, p.right, "reduced2", "reduced2_cls", JReduceWorkingEnv.classCollapse);
        env.setTemp();
        env.removeOldArtifacts();
        // Test if the bug is ASM-preserving
        final boolean isAsmPreserved = env.runIdentity();
        if (!isAsmPreserved) {
            System.out.println("Not ASM preserved");
            return;
        }
        env.runSingle(path);
    }

    public static void testDebug() throws IOException {
        ClassReader cr = DebugClass.getClassReader();
        PrintWriter printer = new PrintWriter(System.out);
        TraceClassVisitor tcv = new TraceClassVisitor(printer);
        Hierarchy hierarchy = new Hierarchy();

        ClassAnalyzeOptions options = new ClassAnalyzeOptions();
        CheckClassAdapter cca = new CheckClassAdapter(tcv, true);

        ClassAnalyzer ca = new ClassAnalyzer(hierarchy, options, null);
        ClassNode cn = new ClassNode(ASM9);
        // CheckClassAdapter.verify(cr, null, true, new PrintWriter(System.out));

        cr.accept(ca, ClassReader.SKIP_DEBUG);
        cr.accept(cn, ClassReader.SKIP_DEBUG);

        // ca.accept(tcv, new TreeSet<>());
        // System.out.println("-------");
        ca.accept(cca, cn, new TreeSet<>());
        // ca.accept(tcv);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final Options options = new Options();
        options.addOption("w", "wf",true, "working folder");
        options.addOption("t", "target",true, "target path");
        options.addOption("l", "lib", true, "library path");
        options.addOption("c", "class", true, "class path");
        options.addOption("p", "predicate", true, "predicate path");
        final CommandLineParser parser = new GnuParser();

        try {
            final CommandLine cmd = parser.parse(options, args);
            if (!cmd.hasOption("l") || !cmd.hasOption("c")
                    || !cmd.hasOption("p") || !cmd.hasOption("t")
                    || !cmd.hasOption("w"))  {
                System.out.println("Missing necessary command-line arguments");
                final HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("options", options);
                return;
            }
            final Path libPath = Paths.get(cmd.getOptionValue("l"));
            final Path classPath = Paths.get(cmd.getOptionValue("c"));
            final Path predicatePath = Paths.get(cmd.getOptionValue("p"));
            final Path targetPath = Paths.get(cmd.getOptionValue("t"));
            final Path workingFolder = Paths.get(cmd.getOptionValue("w"));

            final GeneralWorkingEnv env = new GeneralWorkingEnv(
                    classPath, libPath, predicatePath, targetPath, workingFolder, GeneralWorkingEnv.methodRemoval);
            final boolean isAsmPreserved = env.runIdentity();
            if (!isAsmPreserved) {
                System.out.println("Not ASM preserved");
                return;
            }
            final Pair<Set<Integer>, Boolean> results = env.runReduction();
            System.out.println(results);

        } catch (ParseException e) {
            System.out.println("Get exception: " + e);
            System.out.println("Error parsing the command-line arguments");
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("options", options);
        }
    }
}
