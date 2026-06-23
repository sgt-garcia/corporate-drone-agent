package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.KnowledgeFolderDto;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.KnowledgeRetrievalMode;
import ai.corporatedroneagent.model.KnowledgeToolSettings;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owns general application settings and the local knowledge-folder vertical. The Jira and Confluence
 * verticals live in {@link JiraSettingsService} / {@link ConfluenceSettingsService}; this service
 * delegates to them only to fold their sanitized connection + configured items into the aggregate
 * settings response built by {@link #get()} / {@link #save(ApplicationSettings)}.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private static final int MAX_KNOWLEDGE_FOLDERS = 10;
    // Bounds for the knowledge retrieval knobs, kept in step with the Settings UI steppers.
    private static final int MIN_KNOWLEDGE_RESULTS = 1;
    private static final int MAX_KNOWLEDGE_RESULTS = 50;
    private static final int MIN_KNOWLEDGE_LENGTH = 500;
    private static final int MAX_KNOWLEDGE_LENGTH = 20000;

    private final SettingsRepository settingsRepository;
    private final KnowledgeRootRepository knowledgeRootRepository;
    private final SettingsSecretsService settingsSecretsService;
    private final EventService eventService;
    private final KnowledgeRootCleanupService knowledgeRootCleanupService;
    private final KnowledgeScanCoordinator knowledgeScanCoordinator;
    private final KnowledgeIngestionService ingestionService;
    private final JiraSettingsService jiraSettingsService;
    private final ConfluenceSettingsService confluenceSettingsService;

    public SettingsService(
            SettingsRepository settingsRepository,
            KnowledgeRootRepository knowledgeRootRepository,
            SettingsSecretsService settingsSecretsService,
            EventService eventService,
            KnowledgeRootCleanupService knowledgeRootCleanupService,
            KnowledgeScanCoordinator knowledgeScanCoordinator,
            KnowledgeIngestionService ingestionService,
            JiraSettingsService jiraSettingsService,
            ConfluenceSettingsService confluenceSettingsService
    ) {
        this.settingsRepository = settingsRepository;
        this.knowledgeRootRepository = knowledgeRootRepository;
        this.settingsSecretsService = settingsSecretsService;
        this.eventService = eventService;
        this.knowledgeRootCleanupService = knowledgeRootCleanupService;
        this.knowledgeScanCoordinator = knowledgeScanCoordinator;
        this.ingestionService = ingestionService;
        this.jiraSettingsService = jiraSettingsService;
        this.confluenceSettingsService = confluenceSettingsService;
    }

    public synchronized ApplicationSettings get() {
        ApplicationSettings settings = settingsRepository.get();
        attachKnowledgeFolders(settings);
        // Sanitize the Atlassian connections before secret status lands on them, then attach their
        // configured items afterwards (attach gates on connected, not on token status).
        settings.setJira(jiraSettingsService.sanitizeJira(settings.getJira()));
        settings.setConfluence(confluenceSettingsService.sanitizeConfluence(settings.getConfluence()));
        settingsSecretsService.clearSecretValues(settings);
        settingsSecretsService.applySecretStatus(settings);
        jiraSettingsService.attachJiraProjects(settings.getJira());
        confluenceSettingsService.attachConfluenceSpaces(settings.getConfluence());
        return settings;
    }

    public synchronized ApplicationSettings getWithSecrets() {
        ApplicationSettings settings = settingsRepository.get();
        attachKnowledgeFolders(settings);
        settingsSecretsService.applySecretValues(settings);
        return settings;
    }

    public synchronized ApplicationSettings save(ApplicationSettings settings) {
        ApplicationSettings current = settingsRepository.get();
        settingsSecretsService.saveSubmittedSecrets(settings);

        current.setAgentName(Strings.defaultIfBlank(settings.getAgentName(), "Corporate Drone's Agent"));
        current.setAiModel(Strings.defaultIfBlank(settings.getAiModel(), "none"));
        current.setCustomInstructions(Strings.emptyIfNull(settings.getCustomInstructions()));
        current.setFilesystemToolEnabled(settings.isFilesystemToolEnabled());
        current.setKnowledgeTool(sanitizeKnowledgeTool(settings.getKnowledgeTool()));
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
        // The Jira connection is owned by the dedicated connect/disconnect endpoints, not the
        // general settings PUT. Preserve the server's connection rather than trusting the client
        // payload: a stale or omitted jira.connected here would otherwise hide every project (reads
        // gate on connected) while its roots live on. Confluence shares the same contract.
        current.setJira(jiraSettingsService.sanitizeJira(current.getJira()));
        current.setConfluence(confluenceSettingsService.sanitizeConfluence(current.getConfluence()));
        settingsSecretsService.clearSecretValues(current);
        settingsSecretsService.applySecretStatus(current);
        settingsRepository.save(current);
        ApplicationSettings savedSettings = get();
        eventService.publish("settings-updated");
        return savedSettings;
    }

    public synchronized List<KnowledgeFolderDto> listKnowledgeFolders() {
        return knowledgeFolders();
    }

    public synchronized KnowledgeFolderDto addKnowledgeFolder(KnowledgeFolderRequest request) {
        String path = Strings.defaultIfBlank(request == null ? "" : request.path(), "");
        if (path.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path is required");
        }
        Path folderPath = existingFolderPath(path);
        List<KnowledgeFolderDto> folders = knowledgeFolders();
        if (folders.size() >= MAX_KNOWLEDGE_FOLDERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Local folder limit reached");
        }
        if (folders.stream().anyMatch(folder -> samePath(folder.path(), path))) {
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
        KnowledgeFolderDto folder = knowledgeFolder(root);
        log.info("Added local knowledge folder {} at {}.", folder.id(), path);
        return folder;
    }

    public synchronized void removeKnowledgeFolder(UUID folderId) {
        KnowledgeRoot removedFolder = findKnowledgeRoot(folderId);
        knowledgeScanCoordinator.cancelScanAndWait(folderId);
        removedFolder = findKnowledgeRoot(folderId);

        knowledgeRootCleanupService.removeLocalFolderRoot(removedFolder.getReference());
        knowledgeRootRepository.findByIdAndSource(folderId, KnowledgeSource.LOCAL_FOLDER)
                .ifPresent(root -> knowledgeRootRepository.delete(root.getId()));
        publishSettingsUpdated();
        log.info("Removed local knowledge folder {} at {}.", folderId, removedFolder.getReference());
    }

    public synchronized KnowledgeFolderDto pauseKnowledgeFolder(UUID folderId) {
        KnowledgeRoot root = findKnowledgeRoot(folderId);
        root.setPaused(true);
        root = knowledgeRootRepository.save(root);
        publishSettingsUpdated();
        log.info("Paused local knowledge folder {}.", folderId);
        return knowledgeFolder(root);
    }

    public synchronized KnowledgeFolderDto resumeKnowledgeFolder(UUID folderId) {
        KnowledgeRoot root = findKnowledgeRoot(folderId);
        root.setPaused(false);
        root = knowledgeRootRepository.save(root);
        publishSettingsUpdated();
        log.info("Resumed local knowledge folder {}.", folderId);
        return knowledgeFolder(root);
    }

    // Not synchronized: a local-folder scan is blocking, and the settings monitor must not be
    // held for its whole duration. The scan mutates the root through the engine, not through
    // SettingsService, and returns the refreshed root to map.
    public KnowledgeFolderDto scanKnowledgeFolder(UUID folderId) {
        KnowledgeRoot root = findKnowledgeRoot(folderId);
        return knowledgeFolder(ingestionService.scan(root));
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

    private void validateNotNestedFolder(Path folderPath, List<KnowledgeFolderDto> existingFolders) {
        for (KnowledgeFolderDto existingFolder : existingFolders) {
            Optional<Path> existingPath = comparableExistingFolderPath(existingFolder.path());
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
        settings.setKnowledgeFolders(knowledgeFolders());
    }

    private List<KnowledgeFolderDto> knowledgeFolders() {
        return knowledgeRootRepository.findBySource(KnowledgeSource.LOCAL_FOLDER).stream()
                .map(this::knowledgeFolder)
                .toList();
    }

    // Canonical KnowledgeRoot -> folder-DTO mapper. KnowledgeFolderScanService
    // delegates here so a scan's return DTO and the settings listing stay in sync
    // (notably the "checked" freshness label, which a separate mapper kept dropping).
    KnowledgeFolderDto knowledgeFolder(KnowledgeRoot root) {
        String status = KnowledgeRootFormatting.status(root);
        return new KnowledgeFolderDto(
                root.getId(),
                root.getReference(),
                status,
                root.getTotalResources(),
                root.getTotalSizeBytes() == 0 ? "" : formatBytes(root.getTotalSizeBytes()),
                nextScan(root),
                KnowledgeRootFormatting.checkedLabel(root),
                "error".equals(status) ? root.getScanMessage() : "");
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

    private String formatBytes(long bytes) {
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

    // Clamp the retrieval knobs to the same bounds the UI enforces, so a hand-crafted or
    // stale payload can't push out-of-range results/length into the live settings. Missing
    // modes fall back to their defaults rather than disabling retrieval.
    private KnowledgeToolSettings sanitizeKnowledgeTool(KnowledgeToolSettings knowledgeTool) {
        KnowledgeToolSettings source = knowledgeTool == null ? new KnowledgeToolSettings() : knowledgeTool;
        KnowledgeToolSettings sanitized = new KnowledgeToolSettings();
        sanitized.setAuto(sanitizeRetrievalMode(source.getAuto(), sanitized.getAuto()));
        sanitized.setSearch(sanitizeRetrievalMode(source.getSearch(), sanitized.getSearch()));
        return sanitized;
    }

    private KnowledgeRetrievalMode sanitizeRetrievalMode(KnowledgeRetrievalMode mode, KnowledgeRetrievalMode fallback) {
        if (mode == null) {
            return fallback;
        }
        return new KnowledgeRetrievalMode(
                mode.isEnabled(),
                clamp(mode.getResults(), MIN_KNOWLEDGE_RESULTS, MAX_KNOWLEDGE_RESULTS, fallback.getResults()),
                clamp(mode.getLength(), MIN_KNOWLEDGE_LENGTH, MAX_KNOWLEDGE_LENGTH, fallback.getLength())
        );
    }

    private int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private boolean samePath(String first, String second) {
        return Strings.defaultIfBlank(first, "").equalsIgnoreCase(Strings.defaultIfBlank(second, ""));
    }

    private void publishSettingsUpdated() {
        eventService.publish("settings-updated");
    }
}
