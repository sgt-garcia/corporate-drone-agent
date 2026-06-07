package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private static final int MAX_KNOWLEDGE_FOLDERS = 10;

    private final SettingsRepository settingsRepository;
    private final KnowledgeRootRepository knowledgeRootRepository;
    private final SettingsSecretsService settingsSecretsService;
    private final EventService eventService;
    private final KnowledgeRootCleanupService knowledgeRootCleanupService;
    private final KnowledgeScanCoordinator knowledgeScanCoordinator;

    public SettingsService(
            SettingsRepository settingsRepository,
            KnowledgeRootRepository knowledgeRootRepository,
            SettingsSecretsService settingsSecretsService,
            EventService eventService,
            KnowledgeRootCleanupService knowledgeRootCleanupService,
            KnowledgeScanCoordinator knowledgeScanCoordinator
    ) {
        this.settingsRepository = settingsRepository;
        this.knowledgeRootRepository = knowledgeRootRepository;
        this.settingsSecretsService = settingsSecretsService;
        this.eventService = eventService;
        this.knowledgeRootCleanupService = knowledgeRootCleanupService;
        this.knowledgeScanCoordinator = knowledgeScanCoordinator;
    }

    public ApplicationSettings get() {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        attachKnowledgeFolders(settings);
        settingsSecretsService.clearSecretValues(settings);
        settingsSecretsService.applySecretStatus(settings);
        return settings;
    }

    public ApplicationSettings getWithSecrets() {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        attachKnowledgeFolders(settings);
        settingsSecretsService.applySecretValues(settings);
        return settings;
    }

    public ApplicationSettings save(ApplicationSettings settings) {
        ApplicationSettings current = settingsRepository.get();
        migratePlaintextSecrets(current);
        migrateLegacyKnowledgeFolders(current);
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
        current.setBedrock(settings.getBedrock());
        current.setGroq(settings.getGroq());
        current.setDeepSeek(settings.getDeepSeek());
        settingsSecretsService.clearSecretValues(current);
        settingsSecretsService.applySecretStatus(current);
        settingsRepository.save(current);
        ApplicationSettings savedSettings = get();
        eventService.publish("settings-updated");
        return savedSettings;
    }

    public synchronized List<KnowledgeFolder> listKnowledgeFolders() {
        return knowledgeFolders();
    }

    public synchronized KnowledgeFolder addKnowledgeFolder(KnowledgeFolderRequest request) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);

        String path = Strings.defaultIfBlank(request == null ? "" : request.path(), "");
        if (path.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path is required");
        }
        Path folderPath = existingFolderPath(path);
        List<KnowledgeFolder> folders = knowledgeFolders();
        if (folders.size() >= MAX_KNOWLEDGE_FOLDERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Local folder limit reached");
        }
        if (folders.stream().anyMatch(folder -> samePath(folder.getPath(), path))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Folder is already configured");
        }
        validateNotNestedFolder(folderPath, folders);

        KnowledgeRoot root = new KnowledgeRoot();
        root.setId(UUID.randomUUID());
        root.setSource(KnowledgeSource.LOCAL_FOLDER);
        root.setReference(path);
        root.setDisplayName(displayName(folderPath));
        root.setPaused(false);
        root.setScanStatus(WorkStatus.TO_DO);
        root = knowledgeRootRepository.save(root);
        publishSettingsUpdated();
        KnowledgeFolder folder = knowledgeFolder(root);
        log.info("Added local knowledge folder {} at {}.", folder.getId(), path);
        return folder;
    }

    public synchronized void removeKnowledgeFolder(UUID folderId) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);

        KnowledgeRoot removedFolder = findKnowledgeRoot(folderId);
        knowledgeScanCoordinator.cancelFolderScanAndWait(folderId);
        removedFolder = findKnowledgeRoot(folderId);

        knowledgeRootCleanupService.removeLocalFolderRoot(removedFolder.getReference());
        knowledgeRootRepository.findByIdAndSource(folderId, KnowledgeSource.LOCAL_FOLDER)
                .ifPresent(root -> knowledgeRootRepository.delete(root.getId()));
        publishSettingsUpdated();
        log.info("Removed local knowledge folder {} at {}.", folderId, removedFolder.getReference());
    }

    public synchronized KnowledgeFolder pauseKnowledgeFolder(UUID folderId) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);
        KnowledgeRoot root = findKnowledgeRoot(folderId);
        root.setPaused(true);
        root = knowledgeRootRepository.save(root);
        publishSettingsUpdated();
        log.info("Paused local knowledge folder {}.", folderId);
        return knowledgeFolder(root);
    }

    public synchronized KnowledgeFolder resumeKnowledgeFolder(UUID folderId) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);
        KnowledgeRoot root = findKnowledgeRoot(folderId);
        root.setPaused(false);
        root = knowledgeRootRepository.save(root);
        publishSettingsUpdated();
        log.info("Resumed local knowledge folder {}.", folderId);
        return knowledgeFolder(root);
    }

    private void migratePlaintextSecrets(ApplicationSettings settings) {
        boolean migrated = settingsSecretsService.migratePlaintextSecrets(settings);
        if (migrated) {
            settingsSecretsService.applySecretStatus(settings);
            settingsRepository.save(settings);
        }
    }

    private void publishSettingsUpdated() {
        eventService.publish("settings-updated");
    }

    private KnowledgeRoot findKnowledgeRoot(UUID folderId) {
        return knowledgeRootRepository.findByIdAndSource(folderId, KnowledgeSource.LOCAL_FOLDER)
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

    private void attachKnowledgeFolders(ApplicationSettings settings) {
        migrateLegacyKnowledgeFolders(settings);
        settings.setKnowledgeFolders(knowledgeFolders());
    }

    private void migrateLegacyKnowledgeFolders(ApplicationSettings settings) {
        List<KnowledgeFolder> legacyFolders = sanitizeKnowledgeFolders(settings.getKnowledgeFolders());
        if (legacyFolders.isEmpty()) {
            return;
        }

        for (KnowledgeFolder folder : legacyFolders) {
            KnowledgeRoot root = knowledgeRootRepository
                    .findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, folder.getPath())
                    .orElseGet(KnowledgeRoot::new);
            if (root.getId() == null) {
                root.setId(folder.getId());
            }
            root.setSource(KnowledgeSource.LOCAL_FOLDER);
            root.setReference(folder.getPath());
            root.setDisplayName(displayName(Path.of(folder.getPath())));
            root.setPaused("paused".equals(folder.getStatus()));
            root.setTotalResources(folder.getFiles());
            root.setScanStatus("scanning".equals(folder.getStatus()) ? WorkStatus.DONE : root.getScanStatus());
            knowledgeRootRepository.save(root);
        }

        settings.setKnowledgeFolders(List.of());
        settingsRepository.save(settings);
    }

    private List<KnowledgeFolder> knowledgeFolders() {
        return knowledgeRootRepository.findBySource(KnowledgeSource.LOCAL_FOLDER).stream()
                .map(this::knowledgeFolder)
                .toList();
    }

    private KnowledgeFolder knowledgeFolder(KnowledgeRoot root) {
        KnowledgeFolder folder = new KnowledgeFolder();
        folder.setId(root.getId());
        folder.setPath(root.getReference());
        folder.setStatus(folderStatus(root));
        folder.setFiles(root.getTotalResources());
        folder.setSize(root.getTotalSizeBytes() == 0
                ? ""
                : KnowledgeFolderScanService.formatBytes(root.getTotalSizeBytes()));
        folder.setNextScan(nextScan(root));
        return folder;
    }

    private String folderStatus(KnowledgeRoot root) {
        if (root.isPaused()) {
            return "paused";
        }
        if (root.getScanStatus() == WorkStatus.IN_PROGRESS) {
            return "scanning";
        }
        return "scanned";
    }

    private String nextScan(KnowledgeRoot root) {
        if (!root.isPaused()
                && root.getScanStatus() == WorkStatus.DONE
                && Boolean.TRUE.equals(root.getScanSuccess())) {
            return "~15 min";
        }
        return "";
    }

    private String displayName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
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
