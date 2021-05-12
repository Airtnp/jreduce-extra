package reduction;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import helper.GlobalConfig;

public class JReducePredicate implements Predicate {
    public final Path workingFolder;
    public final Path predicatePath;
    public final Path compilePath;
    public final Path sourcePath;
    public final Path libPath;

    public final String decompiler;
    public final String expectation;
    public boolean saveDiff;

    public JReducePredicate(final Path workingFolder, final String decompiler,
                            final Path predicatePath, final Path compilePath, final Path sourcePath,
                            final Path libPath, final Path expectationPath,
                            final boolean saveDiff) throws IOException {
        this.predicatePath = predicatePath;
        this.compilePath = compilePath;
        this.sourcePath = sourcePath;
        this.libPath = libPath;
        this.expectation = new String(Files.readAllBytes(expectationPath), StandardCharsets.UTF_8);;
        this.workingFolder = workingFolder;
        this.saveDiff = saveDiff;
        this.decompiler = decompiler;
    }

    public JReducePredicate(final JReduceWorkingEnv env, final String decompiler, final boolean saveDiff) throws IOException {
        this(env.workingFolder, decompiler, env.targetPredicatePath(), env.targetCompilePath(),
                env.currentTargetPath(), env.libPath(), env.expectationPath(), saveDiff);
    }

    public boolean runPredicate() throws IOException, InterruptedException {
        return runCompile(runPrepare());
    }

    public String runPrepare() throws IOException, InterruptedException {
        final StringBuilder accOutput = new StringBuilder();
        String output;

        final ProcessBuilder builder = new ProcessBuilder(
                predicatePath.toString(), sourcePath.toString(), libPath.toString());
        builder.redirectErrorStream(true);
        builder.directory(this.workingFolder.toFile());
        if (GlobalConfig.debugCommand)
            GlobalConfig.println(builder.command().toString());
        final Process process = builder.start();
        final BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));

        while ((output = br.readLine()) != null)
            accOutput.append(output);

        process.waitFor();
        if (process.exitValue() != 0)
            return null;
        process.destroy();

        return Paths.get(accOutput.toString()).getFileName().toString();
    }

    public boolean runCompile(final String srcPath) throws IOException, InterruptedException {
        final StringBuilder accOutput = new StringBuilder();
        String output;

        // compile.sh requires `bash`
        final ProcessBuilder builder = new ProcessBuilder(
                "bash", compilePath.toString(), srcPath, libPath.toString());
        builder.directory(this.workingFolder.toFile());
        builder.redirectErrorStream(true);
        if (GlobalConfig.debugCommand)
            GlobalConfig.println(builder.command().toString());
        final Process process = builder.start();
        final BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));

        while ((output = br.readLine()) != null) {
            accOutput.append(output);
            accOutput.append('\n');
        }

        process.waitFor();
        // compile.sh will return 1 when compiling successes
        final int exitValue = process.exitValue();
        process.destroy();

        if (saveDiff) {
            final List<AbstractDelta<String>> deltas = DiffUtils.diff(
                    Arrays.asList(expectation.split("\n")),
                    Arrays.asList(accOutput.toString().split("\n"))).getDeltas();
            final FileWriter f = new FileWriter("logs/expectation_diff.txt", true);
            if (!deltas.isEmpty()) {
                f.write("Differences: \n");
                deltas.forEach((d) -> {
                    try {
                        f.write("\t" + d.toString() + '\n');
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                // final Path compilerOutput = workingFolder.resolve(decompiler).resolve("compiler.out.txt");
                // final Scanner s = new Scanner(compilerOutput.toFile());
                // while (s.hasNextLine()) { f.write("\t" + s.nextLine() + "\n"); }
                f.flush();
            }
            f.close();
        }

        if (GlobalConfig.debugPredicate) {
            GlobalConfig.println("Compile output: " + accOutput);
        }
        return (exitValue == 0) && (accOutput.toString().equals(expectation));
    }
}
