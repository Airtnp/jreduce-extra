package reduction;

import helper.GlobalConfig;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

public class GeneralPredicate implements Predicate {
    final Path workingFolder;
    final Path predicatePath;

    public GeneralPredicate(Path workingFolder, Path predicatePath) {
        this.predicatePath = predicatePath;
        this.workingFolder = workingFolder;
    }

    public boolean runPredicate() throws IOException, InterruptedException {
        final Pair<Integer, String> results = runPred();
        return results.getLeft() == 0;
    }

    public Pair<Integer, String> runPred() throws IOException, InterruptedException {
        final StringBuilder accOutput = new StringBuilder();
        String output;

        // compile.sh requires `bash`
        final ProcessBuilder builder = new ProcessBuilder("bash", predicatePath.toString());
        builder.directory(workingFolder.toFile());
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

        return ImmutablePair.of(exitValue, accOutput.toString());
    }
}
