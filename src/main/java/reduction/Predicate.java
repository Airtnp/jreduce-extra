package reduction;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

        // @DEBUG
        // System.out.println("Compile output: " + accOutput);
        return (exitValue == 0) && (accOutput.toString().equals(expectation));
    }
}
