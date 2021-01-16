import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import reduction.WorkingEnv;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Main {

    public static void runAll() throws IOException {
        final FileWriter fw = new FileWriter("logs/valid_pairs_func_opt_log.csv", true);
        final CSVPrinter printer = new CSVPrinter(fw, CSVFormat.EXCEL);
        printer.printRecord("name", "predicate", "strategy", "ratio", "asm", "status", "progression");

        final List<ImmutablePair<String, String>> validR = new ArrayList<>();
        final String[] decompilers = {"cfr", "fernflower", "procyon"};
        final File root = new File("/Users/liranxiao/result/full/");
        for (final File f: Objects.requireNonNull(root.listFiles())) {
            if (f.isDirectory() && f.getName().startsWith("url")) {
                final String name = f.getName();
                for (final String decompiler: decompilers) {
                    final WorkingEnv env = new WorkingEnv(name, decompiler);
                    try {
                        final ImmutablePair<String, String> p = ImmutablePair.of(name, decompiler);
                        System.out.println(name + " - " + decompiler);
                        env.setTemp();
                        env.removeFull();

                        // Test if the bug is ASM-preserving
                        final boolean isAsmPreserved = env.runIdentity();
                        // Do reduction only if ASM preserved
                        if (isAsmPreserved) {
                            final String ratio = env.runReduction();
                            validR.add(p);
                            env.moveToFull();
                            printer.printRecord(
                                    name, decompiler, "items+logic+reduced",
                                    ratio, true, "success", env.progressions);
                        } else {
                            printer.printRecord(
                                    name, decompiler, "items+logic+reduced",
                                    "", false, "asm-halt", "");
                        }
                    } catch (final FileNotFoundException ex) {
                        continue;
                    } catch (final Exception ex) {
                        printer.printRecord(
                                name, decompiler, "items+logic+reduced",
                                "", "", ex, "");
                    }
                    fw.flush();
                }
            }
        }
        fw.close();
        System.out.println(validR);
    }

    public static void runWith(final List<ImmutablePair<String, String>> validR) throws IOException, InterruptedException {
        final FileWriter f = new FileWriter("logs/valid_pairs_individual_log.csv", true);
        final CSVPrinter printer = new CSVPrinter(f, CSVFormat.EXCEL);
        printer.printRecord("name", "predicate", "strategy", "ratio", "asm", "status", "progression");

        for (final ImmutablePair<String, String> p: validR) {
            System.out.println(p.left + " - " + p.right);
            try {
                final WorkingEnv env = new WorkingEnv(p.left, p.right);
                env.setTemp();
                env.removeFull();

                final boolean isAsmPreserved = env.runIdentity();
                if (isAsmPreserved) {
                    final String ratio = env.runReduction();
                    env.moveToFull();
                    printer.printRecord(
                            p.left, p.right, "items+logic+reduced",
                            ratio, true, "success", env.progressions.toString());
                } else {
                    printer.printRecord(
                            p.left, p.right, "items+logic+reduced",
                            "", false, "asm-halt", "");
                }
            } catch (final Exception ex) {
                printer.printRecord(
                        p.left, p.right, "items+logic+reduced",
                        "", "", ex, "");
                throw ex;
            }
            f.flush();
        }

        f.close();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final List<ImmutablePair<String, String>> validR = new ArrayList<>();
        validR.add(ImmutablePair.of("urlbacca29020_odnoklassniki_one_nio", "cfr"));
        runWith(validR);
        // runAll();
    }
}
