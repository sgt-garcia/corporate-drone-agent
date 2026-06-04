package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.repository.SettingsRepository;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeFolderScanService {

    private static final String STATUS_PAUSED = "paused";
    private static final String STATUS_SCANNING = "scanning";
    private static final String STATUS_SCANNED = "scanned";

    private final SettingsRepository settingsRepository;
    private final SettingsSecretsService settingsSecretsService;
    private final EventService eventService;
    private final LocalFolderKnowledgeScanService localFolderKnowledgeScanService;

    public KnowledgeFolderScanService(
            SettingsRepository settingsRepository,
            SettingsSecretsService settingsSecretsService,
            EventService eventService,
            LocalFolderKnowledgeScanService localFolderKnowledgeScanService
    ) {
        this.settingsRepository = settingsRepository;
        this.settingsSecretsService = settingsSecretsService;
        this.eventService = eventService;
        this.localFolderKnowledgeScanService = localFolderKnowledgeScanService;
    }

    public synchronized void scanAllFolders() {
        List<UUID> folderIds = settingsRepository.get().getKnowledgeFolders().stream()
                .filter(folder -> !STATUS_PAUSED.equals(folder.getStatus()))
                .map(KnowledgeFolder::getId)
                .toList();

        for (UUID folderId : folderIds) {
            try {
                scanFolder(folderId);
            } catch (RuntimeException exception) {
                // Keep one unavailable folder from preventing the rest of the scheduled scan.
            }
        }
    }

    public synchronized KnowledgeFolder scanFolder(UUID folderId) {
        ApplicationSettings settings = settingsRepository.get();
        KnowledgeFolder folder = findFolder(settings, folderId);
        if (STATUS_PAUSED.equals(folder.getStatus())) {
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

        LocalFolderKnowledgeScanService.ScanResult stats = scanKnowledgeFolder(folder, folderPath);
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
        return folder;
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

    private LocalFolderKnowledgeScanService.ScanResult scanKnowledgeFolder(KnowledgeFolder folder, Path folderPath) {
        try {
            return localFolderKnowledgeScanService.scan(folder, folderPath);
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
