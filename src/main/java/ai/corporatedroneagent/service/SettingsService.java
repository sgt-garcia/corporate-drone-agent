package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.dto.KnowledgeFolderDto;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.util.Strings;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
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
    private static final int MAX_JIRA_PROJECTS = 10;

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
        current.setFilesystemToolEnabled(settings.isFilesystemToolEnabled());
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
        current.setJira(sanitizeJira(settings.getJira()));
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
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);

        String path = Strings.defaultIfBlank(request == null ? "" : request.path(), "");
        if (path.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path is required");
        }
        Path folderPath = existingFolderPath(path);
        List<KnowledgeFolderDto> folders = knowledgeFolders();
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
        KnowledgeFolderDto folder = knowledgeFolder(root);
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

    public synchronized KnowledgeFolderDto pauseKnowledgeFolder(UUID folderId) {
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

    public synchronized KnowledgeFolderDto resumeKnowledgeFolder(UUID folderId) {
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

    private void validateNotNestedFolder(Path folderPath, List<KnowledgeFolderDto> existingFolders) {
        for (KnowledgeFolderDto existingFolder : existingFolders) {
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
        List<KnowledgeFolderDto> legacyFolders = sanitizeKnowledgeFolders(settings.getKnowledgeFolders());
        if (legacyFolders.isEmpty()) {
            return;
        }

        for (KnowledgeFolderDto folder : legacyFolders) {
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

    private List<KnowledgeFolderDto> knowledgeFolders() {
        return knowledgeRootRepository.findBySource(KnowledgeSource.LOCAL_FOLDER).stream()
                .map(this::knowledgeFolder)
                .toList();
    }

    private KnowledgeFolderDto knowledgeFolder(KnowledgeRoot root) {
        KnowledgeFolderDto folder = new KnowledgeFolderDto();
        folder.setId(root.getId());
        folder.setPath(root.getReference());
        folder.setStatus(folderStatus(root));
        folder.setFiles(root.getTotalResources());
        folder.setSize(root.getTotalSizeBytes() == 0
                ? ""
                : KnowledgeFolderScanService.formatBytes(root.getTotalSizeBytes()));
        folder.setNextScan(nextScan(root));
        folder.setChecked(checkedLabel(root));
        return folder;
    }

    // Human-readable "checked …" freshness for a scanned folder, derived from the
    // last successful scan's finish time. Empty while scanning/paused or before a
    // first successful scan, so the UI only shows it on a settled "scanned" row.
    private String checkedLabel(KnowledgeRoot root) {
        if (root.isPaused()
                || root.getScanStatus() != WorkStatus.DONE
                || !Boolean.TRUE.equals(root.getScanSuccess())
                || root.getScanFinishedAt() == null) {
            return "";
        }
        return relativeTime(root.getScanFinishedAt(), Instant.now());
    }

    static String relativeTime(Instant then, Instant now) {
        long seconds = Math.max(0, Duration.between(then, now).getSeconds());
        if (seconds < 45) {
            return "just now";
        }
        long minutes = Math.round(seconds / 60.0);
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = Math.round(minutes / 60.0);
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = Math.round(hours / 24.0);
        return days + (days == 1 ? " day ago" : " days ago");
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

    private List<KnowledgeFolderDto> sanitizeKnowledgeFolders(List<KnowledgeFolderDto> folders) {
        List<KnowledgeFolderDto> sanitized = new ArrayList<>();
        if (folders == null) {
            return sanitized;
        }

        for (KnowledgeFolderDto folder : folders) {
            if (folder == null || folder.getPath() == null || folder.getPath().isBlank()) {
                continue;
            }
            KnowledgeFolderDto sanitizedFolder = new KnowledgeFolderDto();
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

    // Jira has no real API integration yet — connecting and scanning are
    // simulated in the UI. Persist only what the Settings screen needs to render
    // on reload: trimmed connection details, the chosen projects (capped and
    // deduped by key), and the token expiry. The live "scanning" status is a
    // client-side animation, so it settles to "scanned" here. The secret token
    // is handled separately by SettingsSecretsService.
    private JiraSettings sanitizeJira(JiraSettings jira) {
        JiraSettings sanitized = new JiraSettings();
        if (jira == null) {
            return sanitized;
        }
        sanitized.setInstanceUrl(Strings.emptyIfNull(jira.getInstanceUrl()).trim());
        sanitized.setEmail(Strings.emptyIfNull(jira.getEmail()).trim());
        sanitized.setConnected(jira.isConnected());
        sanitized.setTokenExpiresDays(jira.getTokenExpiresDays());
        if (!jira.isConnected()) {
            return sanitized;
        }
        sanitized.setProjects(sanitizeJiraProjects(jira.getProjects()));
        return sanitized;
    }

    private List<JiraProjectDto> sanitizeJiraProjects(List<JiraProjectDto> projects) {
        List<JiraProjectDto> sanitized = new ArrayList<>();
        if (projects == null) {
            return sanitized;
        }
        for (JiraProjectDto project : projects) {
            if (project == null || project.getKey() == null || project.getKey().isBlank()) {
                continue;
            }
            String key = project.getKey().trim();
            if (sanitized.stream().anyMatch(existing -> existing.getKey().equalsIgnoreCase(key))) {
                continue;
            }
            JiraProjectDto sanitizedProject = new JiraProjectDto();
            sanitizedProject.setId(project.getId() == null || project.getId().isBlank()
                    ? UUID.randomUUID().toString()
                    : project.getId());
            sanitizedProject.setKey(key);
            sanitizedProject.setName(project.getName());
            sanitizedProject.setStatus("paused".equals(project.getStatus()) ? "paused" : "scanned");
            sanitizedProject.setIssues(project.getIssues());
            sanitizedProject.setChecked(project.getChecked());
            sanitized.add(sanitizedProject);
            if (sanitized.size() == MAX_JIRA_PROJECTS) {
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
