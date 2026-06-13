package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.KnowledgeFolderDto;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeFolderScanService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFolderScanService.class);

    private static final String STATUS_PAUSED = "paused";
    private static final String FOLDER_MISSING_MESSAGE =
            "Folder not found — it may have been moved, renamed, or unmounted.";

    private final SettingsService settingsService;
    private final KnowledgeRootRepository knowledgeRootRepository;
    private final EventService eventService;
    private final LocalFolderKnowledgeScanService localFolderKnowledgeScanService;
    private final KnowledgeScanCoordinator knowledgeScanCoordinator;

    public KnowledgeFolderScanService(
            SettingsService settingsService,
            KnowledgeRootRepository knowledgeRootRepository,
            EventService eventService,
            LocalFolderKnowledgeScanService localFolderKnowledgeScanService,
            KnowledgeScanCoordinator knowledgeScanCoordinator
    ) {
        this.settingsService = settingsService;
        this.knowledgeRootRepository = knowledgeRootRepository;
        this.eventService = eventService;
        this.localFolderKnowledgeScanService = localFolderKnowledgeScanService;
        this.knowledgeScanCoordinator = knowledgeScanCoordinator;
    }

    public synchronized void scanAllFolders() {
        List<UUID> folderIds = settingsService.listKnowledgeFolders().stream()
                .filter(folder -> !STATUS_PAUSED.equals(folder.getStatus()))
                .map(KnowledgeFolderDto::getId)
                .toList();
        log.info("Starting scheduled knowledge scan for {} local folders.", folderIds.size());

        for (UUID folderId : folderIds) {
            try {
                scanFolder(folderId);
            } catch (RuntimeException exception) {
                log.warn("Scheduled knowledge scan failed for local folder {}.", folderId, exception);
            }
        }
        log.info("Finished scheduled knowledge scan for {} local folders.", folderIds.size());
    }

    public synchronized KnowledgeFolderDto scanFolder(UUID folderId) {
        if (!knowledgeScanCoordinator.tryStartFolderScan(folderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge folder not found");
        }

        try {
            KnowledgeRoot root = findRoot(folderId);
            KnowledgeFolderDto folder = knowledgeFolder(root);
            if (STATUS_PAUSED.equals(folder.getStatus())) {
                log.debug("Skipping paused local knowledge folder {}.", folderId);
                return folder;
            }

            Path folderPath = folderPath(folder.getPath());
            if (!Files.isDirectory(folderPath)) {
                // Persist the failure on the root (like a failed Jira scan) so the
                // folder shows a sticky "error" row, then surface it to the caller.
                recordScanFailure(root, FOLDER_MISSING_MESSAGE);
                publishSettingsUpdated();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path must be an existing folder");
            }

            root = markScanInProgress(root);
            folder = knowledgeFolder(root);
            publishSettingsUpdated();
            log.info("Scanning local knowledge folder {} at {}.", folderId, folderPath);

            LocalFolderKnowledgeScanService.ScanResult stats;
            try {
                stats = scanKnowledgeFolder(folder, folderPath, folderId);
            } catch (ResponseStatusException exception) {
                // Settle the in-progress root as a failure so the folder doesn't
                // stick at "scanning" forever; mirror the Jira scan's error path.
                // Reload first (like the success path below) so we flip the row the
                // scan persisted instead of re-saving our stale pre-scan copy, which
                // would revert the totals the scan recorded.
                recordScanFailure(findRoot(folderId), scanErrorMessage(exception));
                publishSettingsUpdated();
                throw exception;
            } catch (RuntimeException exception) {
                recordScanFailure(findRoot(folderId), "Could not scan folder");
                publishSettingsUpdated();
                throw exception;
            }
            root = findRoot(folderId);
            folder = knowledgeFolder(root);
            if (STATUS_PAUSED.equals(folder.getStatus())) {
                return folder;
            }

            publishSettingsUpdated();
            log.info(
                    "Scanned local knowledge folder {} with {} files and size {}.",
                    folderId,
                    stats.files(),
                    folder.getSize()
            );
            return folder;
        } finally {
            knowledgeScanCoordinator.finishFolderScan(folderId);
        }
    }

    private KnowledgeRoot findRoot(UUID folderId) {
        return knowledgeRootRepository.findByIdAndSource(folderId, KnowledgeSource.LOCAL_FOLDER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge folder not found"));
    }

    private KnowledgeRoot markScanInProgress(KnowledgeRoot root) {
        root.setScanStatus(WorkStatus.IN_PROGRESS);
        root.setScanSuccess(null);
        root.setScanMessage("");
        root.setScanStartedAt(Instant.now());
        root.setScanFinishedAt(null);
        return knowledgeRootRepository.save(root);
    }

    // Mark a settled, failed scan with its reason — the folder DTO derives an
    // "error" status from scanSuccess and surfaces scanMessage to the UI.
    private KnowledgeRoot recordScanFailure(KnowledgeRoot root, String message) {
        root.setScanStatus(WorkStatus.DONE);
        root.setScanSuccess(false);
        root.setScanMessage(message);
        root.setScanFinishedAt(Instant.now());
        return knowledgeRootRepository.save(root);
    }

    private String scanErrorMessage(ResponseStatusException exception) {
        String reason = exception.getReason();
        return reason == null || reason.isBlank() ? "Could not scan folder" : reason;
    }

    private Path folderPath(String path) {
        try {
            return Path.of(path);
        } catch (InvalidPathException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path is invalid");
        }
    }

    private LocalFolderKnowledgeScanService.ScanResult scanKnowledgeFolder(
            KnowledgeFolderDto folder,
            Path folderPath,
            UUID folderId
    ) {
        try {
            return localFolderKnowledgeScanService.scan(
                    folder,
                    folderPath,
                    () -> knowledgeScanCoordinator.isFolderScanCancelled(folderId),
                    KnowledgeScanProgress.emitter(eventService, folderId.toString())
            );
        } catch (LocalFolderKnowledgeScanService.KnowledgeScanException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not scan folder");
        }
    }

    private void publishSettingsUpdated() {
        eventService.publish("settings-updated");
    }

    private KnowledgeFolderDto knowledgeFolder(KnowledgeRoot root) {
        // Single source of truth for the DTO mapping lives in SettingsService so the
        // scan response and the settings listing can't drift (e.g. the "checked" label).
        return settingsService.knowledgeFolder(root);
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unitIndex = -1;
        do {
            value = value / 1024;
            unitIndex++;
        } while (value >= 1024 && unitIndex < units.length - 1);

        return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex]);
    }

}
