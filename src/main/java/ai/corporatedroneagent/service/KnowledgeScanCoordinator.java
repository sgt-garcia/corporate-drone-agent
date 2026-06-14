package ai.corporatedroneagent.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Serializes scans of a single knowledge root across sources. Keyed by the root's
 * UUID (globally unique across source types), so one method family serves every
 * source: dedupe concurrent scans of the same root, and let a remove cancel an
 * in-flight scan and wait for it to stop. Pausing a source does NOT cancel — its
 * derived status flips to "paused" while the scan finishes and its result is dropped.
 */
@Service
public class KnowledgeScanCoordinator {

    private final Set<UUID> runningScans = new HashSet<>();
    private final Set<UUID> cancelledScans = new HashSet<>();

    public synchronized boolean tryStartScan(UUID rootId) {
        if (cancelledScans.contains(rootId)) {
            return false;
        }
        return runningScans.add(rootId);
    }

    public synchronized void finishScan(UUID rootId) {
        runningScans.remove(rootId);
        notifyAll();
    }

    public synchronized boolean isScanCancelled(UUID rootId) {
        return cancelledScans.contains(rootId);
    }

    public synchronized void cancelScanAndWait(UUID rootId) {
        cancelledScans.add(rootId);
        while (runningScans.contains(rootId)) {
            try {
                wait();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for knowledge scan to stop", exception);
            }
        }
    }
}
