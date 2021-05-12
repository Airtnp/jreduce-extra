package reduction;

import java.io.IOException;

public interface Predicate {
    boolean runPredicate() throws IOException, InterruptedException ;
}
