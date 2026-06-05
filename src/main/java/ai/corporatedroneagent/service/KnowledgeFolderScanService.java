package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.repository.SettingsRepository;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
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

    private final SettingsRepository settingsRepository;
    private final SettingsSecretsService settingsSecretsService;
    private final EventService eventService;
    private final LocalFolderKnowledgeScanService localFolderKnowledgeScanService;
    private final KnowledgeScanCoordinator knowledgeScanCoordinator;

    public KnowledgeFolderScanService(
            SettingsRepository settingsRepository,
            SettingsSecretsService settingsSecretsService,
            EventService eventService,
            LocalFolderKnowledgeScanService localFolderKnowledgeScanService,
            KnowledgeScanCoordinator knowledgeScanCoordinator
    ) {
        this.settingsRepository = settingsRepository;
        this.settingsSecretsService = settingsSecretsService;
        this.eventService = eventService;
        this.localFolderKnowledgeScanService = localFolderKnowledgeScanService;
        this.knowledgeScanCoordinator = knowledgeScanCoordinator;
    }

    public synchronized void scanAllFolders() {
        List<UUID> folderIds = settingsRepository.get().getKnowledgeFolders().stream()
                .filter(folder -> !STATUS_PAUSED.equals(folder.getStatus()))
                .map(KnowledgeFolder::getId)
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

    public synchronized KnowledgeFolder scanFolder(UUID folderId) {
        if (!knowledgeScanCoordinator.tryStartFolderScan(folderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge folder not found");
        }

        try {
            ApplicationSettings settings = settingsRepository.get();
            KnowledgeFolder folder = findFolder(settings, folderId);
            if (STATUS_PAUSED.equals(folder.getStatus())) {
                log.debug("Skipping paused local knowledge folder {}.", folderId);
                return folder;
            }

            Path folderPath = folderPath(folder.getPath());
            if (!Files.isDirectory(folderPath)) {
                folder.setStatus(STATUS_SCANNED);
                folder.setNextScan("");
                saveAndPublish(settings);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path must be an existing folder");
            }

            folder.setStatus(STATUS_SCANNING);
            folder.setNextScan("");
            saveAndPublish(settings);
            log.info("Scanning local knowledge folder {} at {}.", folderId, folderPath);

            LocalFolderKnowledgeScanService.ScanResult stats;
            try {
                stats = scanKnowledgeFolder(folder, folderPath, folderId);
            } catch (RuntimeException exception) {
                resetScanningFolder(folderId);
                throw exception;
            }
            settings = settingsRepository.get();
            folder = findFolder(settings, folderId);
            if (STATUS_PAUSED.equals(folder.getStatus())) {
                return folder;
            }

            folder.setFiles(stats.files());
            folder.setSize(formatBytes(stats.bytes()));
            folder.setStatus(STATUS_SCANNED);
            folder.setNextScan("~15 min");
            saveAndPublish(settings);
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

    private void resetScanningFolder(UUID folderId) {
        ApplicationSettings settings = settingsRepository.get();
        settings.getKnowledgeFolders().stream()
                .filter(folder -> folderId.equals(folder.getId()))
                .findFirst()
                .ifPresent(folder -> {
                    folder.setStatus(STATUS_SCANNED);
                    folder.setNextScan("");
                    saveAndPublish(settings);
                });
    }

    private KnowledgeFolder findFolder(ApplicationSettings settings, UUID folderId) {
        return settings.getKnowledgeFolders().stream()
                .filter(folder -> folderId.equals(folder.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge folder not found"));
    }

    private Path folderPath(String path) {
        try {
            return Path.of(path);
        } catch (InvalidPathException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path is invalid");
        }
    }

    private LocalFolderKnowledgeScanService.ScanResult scanKnowledgeFolder(
            KnowledgeFolder folder,
            Path folderPath,
            UUID folderId
    ) {
        try {
            return localFolderKnowledgeScanService.scan(
                    folder,
                    folderPath,
                    () -> knowledgeScanCoordinator.isFolderScanCancelled(folderId)
            );
        } catch (LocalFolderKnowledgeScanService.KnowledgeScanException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not scan folder");
        }
    }

    private void saveAndPublish(ApplicationSettings settings) {
        settingsSecretsService.clearSecretValues(settings);
        settingsSecretsService.applySecretStatus(settings);
        settingsRepository.save(settings);
        eventService.publish("settings-updated", settings);
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

        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unitIndex]);
    }

}
