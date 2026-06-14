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
    private final Set<UUID> cancelledJiraScans = new HashSet<>();

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

    // Dedupe concurrent scans of the same Jira project root, and refuse to start one
    // that is being cancelled — mirroring the folder set. Pausing a project does NOT
    // cancel (its derived status flips to "paused" while the scan finishes); removing
    // it does, so the root can be deleted without a scan still writing to it.
    public synchronized boolean tryStartJiraScan(UUID rootId) {
        if (cancelledJiraScans.contains(rootId)) {
            return false;
        }
        return runningJiraScans.add(rootId);
    }

    public synchronized void finishJiraScan(UUID rootId) {
        runningJiraScans.remove(rootId);
        notifyAll();
    }

    public synchronized boolean isJiraScanCancelled(UUID rootId) {
        return cancelledJiraScans.contains(rootId);
    }

    public synchronized void cancelJiraScanAndWait(UUID rootId) {
        cancelledJiraScans.add(rootId);
        while (runningJiraScans.contains(rootId)) {
            try {
                wait();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Jira scan to stop", exception);
            }
        }
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
