package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.util.Strings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SettingsService {

    private static final int MAX_KNOWLEDGE_FOLDERS = 10;

    private final SettingsRepository settingsRepository;
    private final SettingsSecretsService settingsSecretsService;
    private final EventService eventService;
    private final KnowledgeRootCleanupService knowledgeRootCleanupService;
    private final KnowledgeScanCoordinator knowledgeScanCoordinator;

    public SettingsService(
            SettingsRepository settingsRepository,
            SettingsSecretsService settingsSecretsService,
            EventService eventService,
            KnowledgeRootCleanupService knowledgeRootCleanupService,
            KnowledgeScanCoordinator knowledgeScanCoordinator
    ) {
        this.settingsRepository = settingsRepository;
        this.settingsSecretsService = settingsSecretsService;
        this.eventService = eventService;
        this.knowledgeRootCleanupService = knowledgeRootCleanupService;
        this.knowledgeScanCoordinator = knowledgeScanCoordinator;
    }

    public ApplicationSettings get() {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);
        settingsSecretsService.clearSecretValues(settings);
        settingsSecretsService.applySecretStatus(settings);
        return settings;
    }

    public ApplicationSettings getWithSecrets() {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);
        settingsSecretsService.applySecretValues(settings);
        return settings;
    }

    public ApplicationSettings save(ApplicationSettings settings) {
        ApplicationSettings current = settingsRepository.get();
        migratePlaintextSecrets(current);
        normalizeKnowledgeFolders(current);
        settingsSecretsService.saveSubmittedSecrets(settings);

        current.setAgentName(Strings.defaultIfBlank(settings.getAgentName(), "Corporate Drone's Agent"));
        current.setAiModel(Strings.defaultIfBlank(settings.getAiModel(), "none"));
        current.setCustomInstructions(Strings.emptyIfNull(settings.getCustomInstructions()));
        current.setOpenAi(settings.getOpenAi());
        current.setOpenAiSdk(settings.getOpenAiSdk());
        current.setAzureOpenAi(settings.getAzureOpenAi());
        current.setOllama(settings.getOllama());
        current.setMistral(settings.getMistral());
        current.setGemini(settings.getGemini());
        current.setAnthropic(settings.getAnthropic());
        current.setGroq(settings.getGroq());
        current.setDeepSeek(settings.getDeepSeek());
        settingsSecretsService.clearSecretValues(current);
        settingsSecretsService.applySecretStatus(current);
        settingsRepository.save(current);
        eventService.publish("settings-updated", current);
        return current;
    }

    public synchronized List<KnowledgeFolder> listKnowledgeFolders() {
        return get().getKnowledgeFolders();
    }

    public synchronized KnowledgeFolder addKnowledgeFolder(KnowledgeFolderRequest request) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);

        String path = Strings.defaultIfBlank(request == null ? "" : request.path(), "");
        if (path.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path is required");
        }
        Path folderPath = existingFolderPath(path);
        if (settings.getKnowledgeFolders().size() >= MAX_KNOWLEDGE_FOLDERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Local folder limit reached");
        }
        if (settings.getKnowledgeFolders().stream().anyMatch(folder -> samePath(folder.getPath(), path))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Folder is already configured");
        }
        validateNotNestedFolder(folderPath, settings.getKnowledgeFolders());

        KnowledgeFolder folder = new KnowledgeFolder();
        folder.setId(UUID.randomUUID());
        folder.setPath(path);
        folder.setStatus("scanned");
        settings.getKnowledgeFolders().add(folder);
        saveAndPublish(settings);
        return folder;
    }

    public synchronized void removeKnowledgeFolder(UUID folderId) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);

        KnowledgeFolder removedFolder = findKnowledgeFolder(settings, folderId);
        knowledgeScanCoordinator.cancelFolderScanAndWait(folderId);
        settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);
        removedFolder = findKnowledgeFolder(settings, folderId);
        settings.getKnowledgeFolders().removeIf(folder -> folderId.equals(folder.getId()));

        knowledgeRootCleanupService.removeLocalFolderRoot(removedFolder.getPath());
        saveAndPublish(settings);
    }

    public synchronized KnowledgeFolder pauseKnowledgeFolder(UUID folderId) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);
        KnowledgeFolder folder = findKnowledgeFolder(settings, folderId);
        folder.setStatus("paused");
        folder.setNextScan("");
        saveAndPublish(settings);
        return folder;
    }

    public synchronized KnowledgeFolder resumeKnowledgeFolder(UUID folderId) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);
        KnowledgeFolder folder = findKnowledgeFolder(settings, folderId);
        folder.setStatus("scanned");
        saveAndPublish(settings);
        return folder;
    }

    private void migratePlaintextSecrets(ApplicationSettings settings) {
        boolean migrated = settingsSecretsService.migratePlaintextSecrets(settings);
        if (migrated) {
            settingsSecretsService.applySecretStatus(settings);
            settingsRepository.save(settings);
        }
    }

    private void saveAndPublish(ApplicationSettings settings) {
        settingsSecretsService.clearSecretValues(settings);
        settingsSecretsService.applySecretStatus(settings);
        settingsRepository.save(settings);
        eventService.publish("settings-updated", settings);
    }

    private KnowledgeFolder findKnowledgeFolder(ApplicationSettings settings, UUID folderId) {
        return settings.getKnowledgeFolders().stream()
                .filter(folder -> folderId.equals(folder.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge folder not found"));
    }

    private Path existingFolderPath(String path) {
        Path folderPath;
        try {
            folderPath = Path.of(path);
        } catch (InvalidPathException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path is invalid");
        }
        if (!Files.isDirectory(folderPath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path must be an existing folder");
        }
        try {
            return folderPath.toRealPath();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path must be readable");
        }
    }

    private void validateNotNestedFolder(Path folderPath, List<KnowledgeFolder> existingFolders) {
        for (KnowledgeFolder existingFolder : existingFolders) {
            Optional<Path> existingPath = comparableExistingFolderPath(existingFolder.getPath());
            if (existingPath.isEmpty()) {
                continue;
            }
            if (folderPath.equals(existingPath.get())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Folder is already configured");
            }
            if (folderPath.startsWith(existingPath.get()) || existingPath.get().startsWith(folderPath)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Folders must not be nested inside each other"
                );
            }
        }
    }

    private Optional<Path> comparableExistingFolderPath(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        try {
            Path folderPath = Path.of(path);
            if (!Files.isDirectory(folderPath)) {
                return Optional.empty();
            }
            return Optional.of(folderPath.toRealPath());
        } catch (IOException | InvalidPathException exception) {
            return Optional.empty();
        }
    }

    private void normalizeKnowledgeFolders(ApplicationSettings settings) {
        settings.setKnowledgeFolders(sanitizeKnowledgeFolders(settings.getKnowledgeFolders()));
    }

    private List<KnowledgeFolder> sanitizeKnowledgeFolders(List<KnowledgeFolder> folders) {
        List<KnowledgeFolder> sanitized = new ArrayList<>();
        if (folders == null) {
            return sanitized;
        }

        for (KnowledgeFolder folder : folders) {
            if (folder == null || folder.getPath() == null || folder.getPath().isBlank()) {
                continue;
            }
            KnowledgeFolder sanitizedFolder = new KnowledgeFolder();
            sanitizedFolder.setId(folder.getId() == null ? UUID.randomUUID() : folder.getId());
            sanitizedFolder.setPath(folder.getPath().trim());
            sanitizedFolder.setStatus(sanitizeFolderStatus(folder.getStatus()));
            sanitizedFolder.setFiles(folder.getFiles());
            sanitizedFolder.setSize(folder.getSize());
            sanitizedFolder.setNextScan(folder.getNextScan());
            if (sanitized.stream().noneMatch(existing -> samePath(existing.getPath(), sanitizedFolder.getPath()))) {
                sanitized.add(sanitizedFolder);
            }
            if (sanitized.size() == MAX_KNOWLEDGE_FOLDERS) {
                break;
            }
        }
        return sanitized;
    }

    private boolean samePath(String first, String second) {
        return Strings.defaultIfBlank(first, "").equalsIgnoreCase(Strings.defaultIfBlank(second, ""));
    }

    private String sanitizeFolderStatus(String status) {
        String normalized = Strings.defaultIfBlank(status, "scanned");
        return switch (normalized) {
            case "paused", "scanning" -> normalized;
            default -> "scanned";
        };
    }
}
