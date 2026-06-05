package ai.corporatedroneagent.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeScanCoordinator {

    private final Set<UUID> runningFolderScans = new HashSet<>();
    private final Set<UUID> cancelledFolderScans = new HashSet<>();

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
