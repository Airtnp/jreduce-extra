package reduction;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * [low, high) denotes a reduction point range
 */
public class RPGroup {
    public int low;
    public int high;
    public int attribute;

    public static SortedSet<Integer> emptyGroup = new TreeSet<>();

    public RPGroup(int low, int high, int attribute) {
        this.low = low;
        this.high = high;
        this.attribute = attribute;
    }

    public int maxInRange(final SortedSet<Integer> closure) {
        if (closure.isEmpty() || closure.last() < low) {
            return -1;
        }
        final SortedSet<Integer> rangeSet = closure.tailSet(low).headSet(high);
        if (rangeSet.isEmpty()) {
            return -1;
        }
        return rangeSet.last();
    }

    public SortedSet<Integer> inRange(final SortedSet<Integer> closure) {
        if (closure.isEmpty() || closure.last() < low) {
            return emptyGroup;
        }
        return closure.subSet(low, high);
    }

    public int size() {
        return high - low;
    }
}
