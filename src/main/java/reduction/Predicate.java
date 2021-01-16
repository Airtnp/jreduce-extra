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
import com.github.difflib.patch.Patch;

public class Predicate {
    public final Path workingFolder;
    public final String predicatePath;
    public final String compilePath;
    public final String sourcePath;
    public final String libPath;
    public final String expectation;

    public Predicate(final String predicatePath, final String compilePath, final String sourcePath,
                     final String libPath, final String expectationPath) throws IOException {
        this.predicatePath = predicatePath;
        this.compilePath = compilePath;
        this.sourcePath = sourcePath;
        this.libPath = libPath;
        this.expectation = new String(Files.readAllBytes(Paths.get(expectationPath)), StandardCharsets.UTF_8);;
        this.workingFolder = Paths.get(compilePath).getParent();
    }

    public boolean runPredicate() throws IOException, InterruptedException {
        return runCompile(runPrepare());
    }

    public String runPrepare() throws IOException, InterruptedException {
        final StringBuilder accOutput = new StringBuilder();
        String output;

        final ProcessBuilder builder = new ProcessBuilder(predicatePath, sourcePath, libPath);
        builder.redirectErrorStream(true);
        builder.directory(this.workingFolder.toFile());
        final Process process = builder.start();
        final BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));

        while ((output = br.readLine()) != null)
            accOutput.append(output);

        process.waitFor();
        if (process.exitValue() != 0)
            return null;
        process.destroy();

        // @DEBUG
        // System.out.println("Decompile output: " + accOutput);
        return Paths.get(accOutput.toString()).getFileName().toString();
    }

    public boolean runCompile(final String srcPath) throws IOException, InterruptedException {
        final StringBuilder accOutput = new StringBuilder();
        String output;

        // compile.sh requires `bash`
        final ProcessBuilder builder = new ProcessBuilder("bash", compilePath, srcPath, libPath);
        builder.directory(this.workingFolder.toFile());
        builder.redirectErrorStream(true);
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

        /*
        final List<AbstractDelta<String>> deltas = DiffUtils.diff(
                Arrays.asList(expectation.split("\n")),
                Arrays.asList(accOutput.toString().split("\n"))).getDeltas();
        final FileWriter f = new FileWriter("expectation_log.txt", true);
        f.write("Differences: \n");
        deltas.forEach((d) -> {
                    try {
                        f.write(d.toString() + '\n');
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        f.flush();
        f.close();
         */

        // @DEBUG
        // System.out.println("Compile output: " + accOutput);
        // System.out.println("Expected output: " + expectation);
        return (exitValue == 0) && (accOutput.toString().equals(expectation));
    }
}
