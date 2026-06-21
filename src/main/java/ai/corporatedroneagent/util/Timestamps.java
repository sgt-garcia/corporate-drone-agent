package ai.corporatedroneagent.util;

import java.time.Instant;

public final class Timestamps {

    private Timestamps() {
    }

    /**
     * Millisecond-precision equality used by the source adapters to decide whether a resource's
     * last-modified timestamp changed. Two nulls count as equal; one null does not.
     */
    public static boolean sameInstant(Instant first, Instant second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.toEpochMilli() == second.toEpochMilli();
    }
}
