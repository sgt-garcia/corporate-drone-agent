package ai.corporatedroneagent.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeScanCoordinator {

    private final Set<UUID> runningFolderScans = new HashSet<>();
    private final Set<UUID> cancelledFolderScans = new HashSet<>();
    private final Set<UUID> runningJiraScans = new HashSet<>();

    public synchronized boolean tryStartFolderScan(UUID folderId) {
        if (cancelledFolderScans.contains(folderId)) {
            return false;
        }
        runningFolderScans.add(folderId);
        return true;
    }

    public synchronized void finishFolderScan(UUID folderId) {
        runningFolderScans.remove(folderId);
        notifyAll();
    }

    // Dedupe concurrent scans of the same Jira project root. Returns false when a
    // scan for this root is already in flight, mirroring how the folder set guards
    // a folder. There is no Jira cancellation: pausing a project flips its derived
    // status to "paused" while the running scan finishes and its result is dropped.
    public synchronized boolean tryStartJiraScan(UUID rootId) {
        return runningJiraScans.add(rootId);
    }

    public synchronized void finishJiraScan(UUID rootId) {
        runningJiraScans.remove(rootId);
        notifyAll();
    }

    public synchronized boolean isFolderScanCancelled(UUID folderId) {
        return cancelledFolderScans.contains(folderId);
    }

    public synchronized void cancelFolderScanAndWait(UUID folderId) {
        cancelledFolderScans.add(folderId);
        while (runningFolderScans.contains(folderId)) {
            try {
                wait();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for folder scan to stop", exception);
            }
        }
    }
}
