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
    private static final String STATUS_SCANNING = "scanning";
    private static final String STATUS_SCANNED = "scanned";

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
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path must be an existing folder");
            }

            root = markScanInProgress(root);
            folder = knowledgeFolder(root);
            publishSettingsUpdated();
            log.info("Scanning local knowledge folder {} at {}.", folderId, folderPath);

            LocalFolderKnowledgeScanService.ScanResult stats;
            try {
                stats = scanKnowledgeFolder(folder, folderPath, folderId);
            } catch (RuntimeException exception) {
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
        KnowledgeFolderDto folder = new KnowledgeFolderDto();
        folder.setId(root.getId());
        folder.setPath(root.getReference());
        folder.setStatus(folderStatus(root));
        folder.setFiles(root.getTotalResources());
        folder.setSize(root.getTotalSizeBytes() == 0 ? "" : formatBytes(root.getTotalSizeBytes()));
        folder.setNextScan(nextScan(root));
        return folder;
    }

    private String folderStatus(KnowledgeRoot root) {
        if (root.isPaused()) {
            return STATUS_PAUSED;
        }
        if (root.getScanStatus() == WorkStatus.IN_PROGRESS) {
            return STATUS_SCANNING;
        }
        return STATUS_SCANNED;
    }

    private String nextScan(KnowledgeRoot root) {
        if (!root.isPaused()
                && root.getScanStatus() == WorkStatus.DONE
                && Boolean.TRUE.equals(root.getScanSuccess())) {
            return "~15 min";
        }
        return "";
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
