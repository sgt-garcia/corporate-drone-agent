package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraConnectionRequest;
import ai.corporatedroneagent.dto.JiraConnectionValidationDto;
import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.dto.JiraProjectRequest;
import ai.corporatedroneagent.dto.KnowledgeFolderDto;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeReferences;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private static final int MAX_KNOWLEDGE_FOLDERS = 10;
    private static final int MAX_JIRA_PROJECTS = 10;
    private static final int JIRA_PROJECT_SEARCH_LIMIT = 25;

    private final SettingsRepository settingsRepository;
    private final KnowledgeRootRepository knowledgeRootRepository;
    private final SettingsSecretsService settingsSecretsService;
    private final EventService eventService;
    private final KnowledgeRootCleanupService knowledgeRootCleanupService;
    private final KnowledgeScanCoordinator knowledgeScanCoordinator;
    private final JiraConnectionValidationService jiraConnectionValidationService;
    private final JiraProjectDiscoveryService jiraProjectDiscoveryService;
    private final JiraKnowledgeScanService jiraKnowledgeScanService;

    SettingsService(
            SettingsRepository settingsRepository,
            KnowledgeRootRepository knowledgeRootRepository,
            SettingsSecretsService settingsSecretsService,
            EventService eventService,
            KnowledgeRootCleanupService knowledgeRootCleanupService,
            KnowledgeScanCoordinator knowledgeScanCoordinator
    ) {
        this(
                settingsRepository,
                knowledgeRootRepository,
                settingsSecretsService,
                eventService,
                knowledgeRootCleanupService,
                knowledgeScanCoordinator,
                new JiraConnectionValidationService(),
                new JiraProjectDiscoveryService(new ObjectMapper()),
                null
        );
    }

    @Autowired
    public SettingsService(
            SettingsRepository settingsRepository,
            KnowledgeRootRepository knowledgeRootRepository,
            SettingsSecretsService settingsSecretsService,
            EventService eventService,
            KnowledgeRootCleanupService knowledgeRootCleanupService,
            KnowledgeScanCoordinator knowledgeScanCoordinator,
            JiraConnectionValidationService jiraConnectionValidationService,
            JiraProjectDiscoveryService jiraProjectDiscoveryService,
            JiraKnowledgeScanService jiraKnowledgeScanService
    ) {
        this.settingsRepository = settingsRepository;
        this.knowledgeRootRepository = knowledgeRootRepository;
        this.settingsSecretsService = settingsSecretsService;
        this.eventService = eventService;
        this.knowledgeRootCleanupService = knowledgeRootCleanupService;
        this.knowledgeScanCoordinator = knowledgeScanCoordinator;
        this.jiraConnectionValidationService = jiraConnectionValidationService;
        this.jiraProjectDiscoveryService = jiraProjectDiscoveryService;
        this.jiraKnowledgeScanService = jiraKnowledgeScanService;
    }

    public ApplicationSettings get() {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        attachKnowledgeFolders(settings);
        settings.setJira(sanitizeJira(settings.getJira()));
        settingsSecretsService.clearSecretValues(settings);
        settingsSecretsService.applySecretStatus(settings);
        syncJiraKnowledgeRoots(settings.getJira());
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

    public synchronized JiraSettings getJiraSettings() {
        return currentJiraSettings();
    }

    public synchronized JiraConnectionValidationDto validateJiraConnection(JiraConnectionRequest request) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);
        settingsSecretsService.applySecretStatus(settings);
        boolean hasSavedToken = settings.getJira().isTokenConfigured();
        validateJiraConnectionRequest(request, hasSavedToken);
        String token = jiraValidationToken(request);
        JiraConnectionValidationService.ValidationResult result = jiraConnectionValidationService.validate(
                Strings.defaultIfBlank(request.instanceUrl(), ""),
                Strings.defaultIfBlank(request.email(), ""),
                token
        );
        return new JiraConnectionValidationDto(result.valid(), result.message(), result.liveValidationAvailable());
    }

    public synchronized JiraSettings saveJiraConnection(JiraConnectionRequest request) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);
        settingsSecretsService.applySecretStatus(settings);

        JiraSettings current = settings.getJira();
        String token = Strings.defaultIfBlank(request == null ? "" : request.token(), "");
        if (request != null && request.clearToken() && token.isBlank()) {
            JiraSettings cleared = new JiraSettings();
            settings.setJira(cleared);
            persistJiraSettings(settings, cleared, jiraSecretRequest("", true));
            log.info("Cleared Jira setup.");
            return currentJiraSettings();
        }

        validateJiraConnectionRequest(request, current.isTokenConfigured());
        String validationToken = token.isBlank()
                ? savedJiraToken()
                : token;
        JiraConnectionValidationService.ValidationResult validation = jiraConnectionValidationService.validate(
                Strings.defaultIfBlank(request.instanceUrl(), ""),
                Strings.defaultIfBlank(request.email(), ""),
                validationToken
        );
        if (!validation.valid()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, validation.message());
        }
        JiraSettings next = new JiraSettings();
        next.setInstanceUrl(Strings.defaultIfBlank(request.instanceUrl(), ""));
        next.setEmail(Strings.defaultIfBlank(request.email(), ""));
        next.setConnected(true);
        next.setTokenExpiresDays(current.getTokenExpiresDays() == null ? 90 : current.getTokenExpiresDays());
        next.setProjects(current.isConnected() ? current.getProjects() : List.of());

        persistJiraSettings(settings, next, token.isBlank() ? null : jiraSecretRequest(token, false));
        log.info("Saved Jira setup for {}.", next.getInstanceUrl());
        return currentJiraSettings();
    }

    public synchronized JiraSettings clearJiraConnection() {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);
        JiraSettings cleared = new JiraSettings();
        settings.setJira(cleared);
        persistJiraSettings(settings, cleared, jiraSecretRequest("", true));
        log.info("Cleared Jira setup.");
        return currentJiraSettings();
    }

    public synchronized List<JiraProjectDto> listJiraProjects() {
        return currentJiraSettings().getProjects();
    }

    public synchronized void scanAllJiraProjects() {
        JiraSettings jira = currentJiraSettings();
        if (!jira.isConnected() || !jira.isTokenConfigured()) {
            log.debug("Skipping scheduled Jira project scan because Jira is not connected.");
            return;
        }

        List<String> projectIds = jira.getProjects().stream()
                .filter(project -> !"paused".equals(project.getStatus()))
                .map(JiraProjectDto::getId)
                .toList();
        log.info("Starting scheduled Jira scan for {} projects.", projectIds.size());

        for (String projectId : projectIds) {
            try {
                scanJiraProject(projectId, true);
            } catch (RuntimeException exception) {
                log.warn("Scheduled Jira scan failed for project {}.", projectId, exception);
            }
        }
        log.info("Finished scheduled Jira scan for {} projects.", projectIds.size());
    }

    public synchronized List<JiraProjectDto> searchJiraProjects(String query) {
        JiraSettings jira = currentJiraSettings();
        requireJiraSetup(jira);
        return jiraProjectDiscoveryService.searchProjects(
                        jira.getInstanceUrl(),
                        jira.getEmail(),
                        savedJiraToken(),
                        query,
                        JIRA_PROJECT_SEARCH_LIMIT
                ).stream()
                .filter(project -> jira.getProjects().stream()
                        .noneMatch(configured -> configured.getKey().equalsIgnoreCase(project.getKey())))
                .toList();
    }

    public synchronized JiraProjectDto addJiraProject(JiraProjectRequest request) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);
        settingsSecretsService.applySecretStatus(settings);
        JiraSettings jira = settings.getJira();
        requireJiraSetup(jira);

        String key = normalizeJiraProjectKey(request == null ? "" : request.key());
        if (jira.getProjects().stream().anyMatch(project -> project.getKey().equalsIgnoreCase(key))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Jira project is already configured");
        }
        if (jira.getProjects().size() >= MAX_JIRA_PROJECTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira project limit reached");
        }

        List<JiraProjectDto> projects = new ArrayList<>(jira.getProjects());
        JiraProjectDto project = jiraProjectDiscoveryService.getProject(
                jira.getInstanceUrl(),
                jira.getEmail(),
                savedJiraToken(),
                key
        );
        projects.add(project);
        jira.setProjects(projects);
        persistJiraSettings(settings, jira, null);
        log.info("Added Jira project {}.", key);
        return project;
    }

    public synchronized void removeJiraProject(String projectId) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);
        settingsSecretsService.applySecretStatus(settings);
        JiraSettings jira = settings.getJira();
        requireJiraSetup(jira);
        List<JiraProjectDto> projects = jira.getProjects().stream()
                .filter(project -> !project.getId().equals(projectId))
                .toList();
        if (projects.size() == jira.getProjects().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira project not found");
        }
        jira.setProjects(projects);
        persistJiraSettings(settings, jira, null);
        log.info("Removed Jira project {}.", projectId);
    }

    public synchronized JiraProjectDto scanJiraProject(String projectId) {
        return scanJiraProject(projectId, false);
    }

    private JiraProjectDto scanJiraProject(String projectId, boolean scheduled) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);
        settingsSecretsService.applySecretStatus(settings);
        JiraSettings jira = settings.getJira();
        requireJiraSetup(jira);

        JiraProjectDto target = jira.getProjects().stream()
                .filter(project -> project.getId().equals(projectId))
                .findFirst()
                .map(this::copyJiraProject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira project not found"));

        JiraKnowledgeScanService.ScanResult scanResult;
        if (jiraKnowledgeScanService == null) {
            scanResult = new JiraKnowledgeScanService.ScanResult(target.getIssues(), 0);
        } else {
            if (scheduled) {
                scanResult = jiraKnowledgeScanService.scanScheduledProject(
                        jira,
                        target,
                        savedJiraToken(),
                        KnowledgeScanProgress.emitter(eventService, target.getId())
                );
            } else {
                scanResult = jiraKnowledgeScanService.scanProject(
                        jira,
                        target,
                        savedJiraToken(),
                        KnowledgeScanProgress.emitter(eventService, target.getId())
                );
            }
        }

        return replaceJiraProject(settings, jira, projectId, project -> {
            project.setStatus("scanned");
            project.setIssues(scanResult.issues());
            project.setChecked("just now");
        });
    }

    public synchronized JiraProjectDto pauseJiraProject(String projectId) {
        return updateJiraProject(projectId, project -> project.setStatus("paused"));
    }

    public synchronized JiraProjectDto resumeJiraProject(String projectId) {
        return updateJiraProject(projectId, project -> project.setStatus("scanned"));
    }

    private JiraSettings currentJiraSettings() {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);
        settings.setJira(sanitizeJira(settings.getJira()));
        settingsSecretsService.applySecretStatus(settings);
        syncJiraKnowledgeRoots(settings.getJira());
        return settings.getJira();
    }

    private void validateJiraConnectionRequest(JiraConnectionRequest request, boolean hasSavedToken) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira connection details are required");
        }
        String instanceUrl = Strings.defaultIfBlank(request.instanceUrl(), "");
        if (instanceUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira instance URL is required");
        }
        if (!instanceUrl.startsWith("https://") && !instanceUrl.startsWith("http://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira instance URL must start with https://");
        }
        String email = Strings.defaultIfBlank(request.email(), "");
        if (email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira email is required");
        }
        if (!email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira email must be valid");
        }
        String token = Strings.defaultIfBlank(request.token(), "");
        if (token.isBlank() && !hasSavedToken) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira API token is required");
        }
    }

    private String jiraValidationToken(JiraConnectionRequest request) {
        String submittedToken = Strings.defaultIfBlank(request.token(), "");
        return submittedToken.isBlank() ? savedJiraToken() : submittedToken;
    }

    private String savedJiraToken() {
        ApplicationSettings secretValues = new ApplicationSettings();
        settingsSecretsService.applySecretValues(secretValues);
        return secretValues.getJira().getToken();
    }

    private void persistJiraSettings(
            ApplicationSettings settings,
            JiraSettings jira,
            ApplicationSettings secretRequest
    ) {
        if (secretRequest != null) {
            settingsSecretsService.saveSubmittedSecrets(secretRequest);
        }
        JiraSettings sanitizedJira = sanitizeJira(jira);
        settings.setJira(sanitizedJira);
        settingsSecretsService.clearSecretValues(settings);
        settingsSecretsService.applySecretStatus(settings);
        settingsRepository.save(settings);
        syncJiraKnowledgeRoots(sanitizedJira);
        publishSettingsUpdated();
    }

    private ApplicationSettings jiraSecretRequest(String token, boolean clearToken) {
        ApplicationSettings settings = new ApplicationSettings();
        settings.getJira().setToken(token);
        settings.getJira().setClearToken(clearToken);
        return settings;
    }

    private void requireJiraSetup(JiraSettings jira) {
        if (!jira.isConnected() || !jira.isTokenConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Save Jira setup before managing projects");
        }
    }

    private void syncJiraKnowledgeRoots(JiraSettings jira) {
        List<String> expectedReferences = jira.isConnected()
                ? jira.getProjects().stream()
                .map(project -> jiraProjectRootReference(jira, project))
                .toList()
                : List.of();

        knowledgeRootRepository.findBySource(KnowledgeSource.JIRA).stream()
                .filter(root -> !expectedReferences.contains(root.getReference()))
                .forEach(knowledgeRootCleanupService::removeRoot);

        if (!jira.isConnected()) {
            return;
        }

        for (JiraProjectDto project : jira.getProjects()) {
            String reference = jiraProjectRootReference(jira, project);
            KnowledgeRoot root = knowledgeRootRepository
                    .findBySourceAndReference(KnowledgeSource.JIRA, reference)
                    .orElseGet(KnowledgeRoot::new);
            boolean newRoot = root.getId() == null;
            root.setSource(KnowledgeSource.JIRA);
            root.setReference(reference);
            root.setDisplayName(jiraProjectDisplayName(project));
            root.setPaused("paused".equals(project.getStatus()));
            root.setTotalResources(project.getIssues());
            if (newRoot) {
                root.setTotalSizeBytes(0);
            }
            if (root.getScanStatus() == WorkStatus.IN_PROGRESS) {
                root.setScanStatus(WorkStatus.TO_DO);
                root.setScanSuccess(null);
                root.setScanMessage("");
                root.setScanStartedAt(null);
                root.setScanFinishedAt(null);
            }
            knowledgeRootRepository.save(root);
        }
    }

    private String jiraProjectRootReference(JiraSettings jira, JiraProjectDto project) {
        return JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), jiraProjectReferenceId(project));
    }

    private String jiraProjectReferenceId(JiraProjectDto project) {
        String projectId = Strings.defaultIfBlank(project.getId(), "").trim();
        return projectId.isBlank() ? project.getKey() : projectId;
    }

    private String jiraProjectDisplayName(JiraProjectDto project) {
        String key = Strings.defaultIfBlank(project.getKey(), "").trim();
        String name = Strings.defaultIfBlank(project.getName(), "").trim();
        if (key.isBlank()) {
            return name;
        }
        if (name.isBlank()) {
            return key;
        }
        return key + " - " + name;
    }

    private JiraProjectDto updateJiraProject(String projectId, Consumer<JiraProjectDto> updater) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        migrateLegacyKnowledgeFolders(settings);
        settingsSecretsService.applySecretStatus(settings);
        JiraSettings jira = settings.getJira();
        requireJiraSetup(jira);

        JiraProjectDto updatedProject = null;
        List<JiraProjectDto> projects = new ArrayList<>();
        for (JiraProjectDto project : jira.getProjects()) {
            JiraProjectDto copy = copyJiraProject(project);
            if (copy.getId().equals(projectId)) {
                updater.accept(copy);
                updatedProject = copy;
            }
            projects.add(copy);
        }
        if (updatedProject == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira project not found");
        }
        jira.setProjects(projects);
        persistJiraSettings(settings, jira, null);
        return updatedProject;
    }

    private JiraProjectDto replaceJiraProject(
            ApplicationSettings settings,
            JiraSettings jira,
            String projectId,
            Consumer<JiraProjectDto> updater
    ) {
        JiraProjectDto updatedProject = null;
        List<JiraProjectDto> projects = new ArrayList<>();
        for (JiraProjectDto project : jira.getProjects()) {
            JiraProjectDto copy = copyJiraProject(project);
            if (copy.getId().equals(projectId)) {
                updater.accept(copy);
                updatedProject = copy;
            }
            projects.add(copy);
        }
        if (updatedProject == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira project not found");
        }
        jira.setProjects(projects);
        persistJiraSettings(settings, jira, null);
        return updatedProject;
    }

    private JiraProjectDto copyJiraProject(JiraProjectDto source) {
        JiraProjectDto copy = new JiraProjectDto();
        copy.setId(source.getId());
        copy.setKey(source.getKey());
        copy.setName(source.getName());
        copy.setStatus(source.getStatus());
        copy.setIssues(source.getIssues());
        copy.setChecked(source.getChecked());
        return copy;
    }

    private String normalizeJiraProjectKey(String key) {
        String normalized = Strings.defaultIfBlank(key, "").toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira project key is required");
        }
        return normalized;
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

    // Persist only what the Settings screen needs to render on reload: trimmed
    // connection details, the chosen projects (capped and deduped by key), and
    // the token expiry. The secret token is handled separately by
    // SettingsSecretsService.
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
