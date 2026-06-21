package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ConfluenceConnectionRequest;
import ai.corporatedroneagent.dto.ConfluenceConnectionValidationDto;
import ai.corporatedroneagent.dto.ConfluenceSpaceDto;
import ai.corporatedroneagent.dto.ConfluenceSpaceRequest;
import ai.corporatedroneagent.dto.JiraConnectionRequest;
import ai.corporatedroneagent.dto.JiraConnectionValidationDto;
import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.dto.JiraProjectRequest;
import ai.corporatedroneagent.dto.KnowledgeFolderDto;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.ConfluenceSettings;
import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.model.KnowledgeRetrievalMode;
import ai.corporatedroneagent.model.KnowledgeToolSettings;
import ai.corporatedroneagent.model.knowledge.ConfluenceKnowledgeReferences;
import ai.corporatedroneagent.model.knowledge.ConfluenceKnowledgeRootConfig;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeReferences;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeRootConfig;
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
    private static final int MAX_CONFLUENCE_SPACES = 10;
    private static final int CONFLUENCE_SPACE_SEARCH_LIMIT = 25;
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
    private final JiraConnectionValidationService jiraConnectionValidationService;
    private final JiraProjectDiscoveryService jiraProjectDiscoveryService;
    private final ConfluenceConnectionValidationService confluenceConnectionValidationService;
    private final ConfluenceSpaceDiscoveryService confluenceSpaceDiscoveryService;

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
                new ConfluenceConnectionValidationService(),
                new ConfluenceSpaceDiscoveryService(new ObjectMapper())
        );
    }

    // Test convenience: inject mock Jira services while defaulting the Confluence ones,
    // for tests that only exercise the Jira vertical.
    SettingsService(
            SettingsRepository settingsRepository,
            KnowledgeRootRepository knowledgeRootRepository,
            SettingsSecretsService settingsSecretsService,
            EventService eventService,
            KnowledgeRootCleanupService knowledgeRootCleanupService,
            KnowledgeScanCoordinator knowledgeScanCoordinator,
            JiraConnectionValidationService jiraConnectionValidationService,
            JiraProjectDiscoveryService jiraProjectDiscoveryService
    ) {
        this(
                settingsRepository,
                knowledgeRootRepository,
                settingsSecretsService,
                eventService,
                knowledgeRootCleanupService,
                knowledgeScanCoordinator,
                jiraConnectionValidationService,
                jiraProjectDiscoveryService,
                new ConfluenceConnectionValidationService(),
                new ConfluenceSpaceDiscoveryService(new ObjectMapper())
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
            ConfluenceConnectionValidationService confluenceConnectionValidationService,
            ConfluenceSpaceDiscoveryService confluenceSpaceDiscoveryService
    ) {
        this.settingsRepository = settingsRepository;
        this.knowledgeRootRepository = knowledgeRootRepository;
        this.settingsSecretsService = settingsSecretsService;
        this.eventService = eventService;
        this.knowledgeRootCleanupService = knowledgeRootCleanupService;
        this.knowledgeScanCoordinator = knowledgeScanCoordinator;
        this.jiraConnectionValidationService = jiraConnectionValidationService;
        this.jiraProjectDiscoveryService = jiraProjectDiscoveryService;
        this.confluenceConnectionValidationService = confluenceConnectionValidationService;
        this.confluenceSpaceDiscoveryService = confluenceSpaceDiscoveryService;
    }

    public synchronized ApplicationSettings get() {
        ApplicationSettings settings = settingsRepository.get();
        attachKnowledgeFolders(settings);
        settings.setJira(sanitizeJira(settings.getJira()));
        settings.setConfluence(sanitizeConfluence(settings.getConfluence()));
        settingsSecretsService.clearSecretValues(settings);
        settingsSecretsService.applySecretStatus(settings);
        attachJiraProjects(settings.getJira());
        attachConfluenceSpaces(settings.getConfluence());
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
        // The Jira connection is owned by the dedicated connect/disconnect endpoints,
        // not the general settings PUT. Preserve the server's connection rather than
        // trusting the client payload: a stale or omitted jira.connected here would
        // otherwise hide every project (reads gate on connected) while its roots live on.
        current.setJira(sanitizeJira(current.getJira()));
        // Confluence shares Jira's contract: its connection is owned by the dedicated
        // connect/disconnect endpoints, and its spaces live in CONFLUENCE roots — never
        // imported from the general settings PUT.
        current.setConfluence(sanitizeConfluence(current.getConfluence()));
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
        ApplicationSettings settings = settingsRepository.get();
        KnowledgeRoot root = findKnowledgeRoot(folderId);
        root.setPaused(true);
        root = knowledgeRootRepository.save(root);
        publishSettingsUpdated();
        log.info("Paused local knowledge folder {}.", folderId);
        return knowledgeFolder(root);
    }

    public synchronized KnowledgeFolderDto resumeKnowledgeFolder(UUID folderId) {
        ApplicationSettings settings = settingsRepository.get();
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
        settingsSecretsService.applySecretStatus(settings);
        boolean hasSavedToken = settings.getJira().isTokenConfigured();
        validateJiraConnectionRequest(request, hasSavedToken);
        String token = jiraValidationToken(request);
        JiraConnectionValidationService.ValidationResult result = jiraConnectionValidationService.validate(
                Strings.defaultIfBlank(request.instanceUrl(), ""),
                Strings.defaultIfBlank(request.email(), ""),
                token
        );
        return new JiraConnectionValidationDto(
                result.valid(),
                result.message(),
                result.liveValidationAvailable(),
                result.apiVersion()
        );
    }

    public synchronized JiraSettings saveJiraConnection(JiraConnectionRequest request) {
        ApplicationSettings settings = settingsRepository.get();
        settingsSecretsService.applySecretStatus(settings);

        JiraSettings current = settings.getJira();
        boolean wasConnected = current.isConnected();
        String token = Strings.defaultIfBlank(request == null ? "" : request.token(), "");
        if (request != null && request.clearToken() && token.isBlank()) {
            JiraSettings cleared = new JiraSettings();
            settings.setJira(cleared);
            removeAllJiraRoots();
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
        next.setApiVersion(validation.apiVersion());
        next.setTokenExpiresDays(current.getTokenExpiresDays() == null ? 90 : current.getTokenExpiresDays());
        if (!wasConnected) {
            // Fresh connect from disconnected: drop any stale roots so the new connection
            // starts clean (parity with the old projects=List.of() reset).
            removeAllJiraRoots();
        }

        persistJiraSettings(settings, next, token.isBlank() ? null : jiraSecretRequest(token, false));
        log.info("Saved Jira setup for {}.", next.getInstanceUrl());
        return currentJiraSettings();
    }

    public synchronized JiraSettings clearJiraConnection() {
        ApplicationSettings settings = settingsRepository.get();
        JiraSettings cleared = new JiraSettings();
        settings.setJira(cleared);
        removeAllJiraRoots();
        persistJiraSettings(settings, cleared, jiraSecretRequest("", true));
        log.info("Cleared Jira setup.");
        return currentJiraSettings();
    }

    public synchronized List<JiraProjectDto> listJiraProjects() {
        return currentJiraSettings().getProjects();
    }

    public synchronized List<JiraProjectDto> searchJiraProjects(String query) {
        JiraSettings jira = currentJiraSettings();
        requireJiraSetup(jira);
        return jiraProjectDiscoveryService.searchProjects(
                        jira.getInstanceUrl(),
                        jira.getEmail(),
                        savedJiraToken(),
                        query,
                        JIRA_PROJECT_SEARCH_LIMIT,
                        jira.getApiVersion()
                ).stream()
                .filter(project -> jira.getProjects().stream()
                        .noneMatch(configured -> configured.getKey().equalsIgnoreCase(project.getKey())))
                .toList();
    }

    public synchronized JiraProjectDto addJiraProject(JiraProjectRequest request) {
        ApplicationSettings settings = settingsRepository.get();
        settingsSecretsService.applySecretStatus(settings);
        JiraSettings jira = settings.getJira();
        requireJiraSetup(jira);

        String key = normalizeJiraProjectKey(request == null ? "" : request.key());
        List<KnowledgeRoot> existing = knowledgeRootRepository.findBySource(KnowledgeSource.JIRA);
        if (existing.stream().anyMatch(root -> key.equalsIgnoreCase(JiraKnowledgeRootConfig.readKey(root)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Jira project is already configured");
        }
        if (existing.size() >= MAX_JIRA_PROJECTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira project limit reached");
        }

        JiraProjectDto discovered = jiraProjectDiscoveryService.getProject(
                jira.getInstanceUrl(),
                jira.getEmail(),
                savedJiraToken(),
                key,
                jira.getApiVersion()
        );
        String projectId = Strings.defaultIfBlank(discovered.getId(), "").trim();
        if (projectId.isBlank()) {
            projectId = UUID.randomUUID().toString();
        }
        KnowledgeRoot root = new KnowledgeRoot();
        root.setSource(KnowledgeSource.JIRA);
        root.setReference(JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), projectId));
        root.setDisplayName(jiraDisplayName(discovered.getKey(), discovered.getName()));
        root.setTotalResources(discovered.getIssues());
        root.setConfigJson(JiraKnowledgeRootConfig.withIdentity(
                null, projectId, discovered.getKey(), discovered.getName()));
        root = knowledgeRootRepository.save(root);
        publishSettingsUpdated();
        log.info("Added Jira project {}.", key);
        return jiraProject(root);
    }

    public synchronized void removeJiraProject(String projectId) {
        ApplicationSettings settings = settingsRepository.get();
        settingsSecretsService.applySecretStatus(settings);
        requireJiraSetup(settings.getJira());
        KnowledgeRoot root = findJiraRoot(projectId);
        // Stop any in-flight scan before deleting the root so the scan can't keep writing
        // to it (mirrors removeKnowledgeFolder). The scan stops between issues.
        knowledgeScanCoordinator.cancelScanAndWait(root.getId());
        KnowledgeRoot current = knowledgeRootRepository.findById(root.getId()).orElse(root);
        knowledgeRootCleanupService.removeRoot(current);
        publishSettingsUpdated();
        log.info("Removed Jira project {}.", projectId);
    }

    public synchronized JiraProjectDto pauseJiraProject(String projectId) {
        return setJiraProjectPaused(projectId, true);
    }

    public synchronized JiraProjectDto resumeJiraProject(String projectId) {
        return setJiraProjectPaused(projectId, false);
    }

    private JiraProjectDto setJiraProjectPaused(String projectId, boolean paused) {
        ApplicationSettings settings = settingsRepository.get();
        settingsSecretsService.applySecretStatus(settings);
        requireJiraSetup(settings.getJira());
        KnowledgeRoot root = findJiraRoot(projectId);
        root.setPaused(paused);
        root = knowledgeRootRepository.save(root);
        publishSettingsUpdated();
        return jiraProject(root);
    }

    private JiraSettings currentJiraSettings() {
        ApplicationSettings settings = settingsRepository.get();
        settings.setJira(sanitizeJira(settings.getJira()));
        settingsSecretsService.applySecretStatus(settings);
        attachJiraProjects(settings.getJira());
        return settings.getJira();
    }

    // ---------------------------------------------------------------------------------------
    // Confluence (API) — sibling of the Jira vertical above. Same connect/scan archetype: the
    // connection lives in settings + the secret store, the chosen spaces live in CONFLUENCE
    // roots (the sole record), and indexing runs through the shared knowledge pipeline.
    // ---------------------------------------------------------------------------------------

    public synchronized ConfluenceSettings getConfluenceSettings() {
        return currentConfluenceSettings();
    }

    public synchronized ConfluenceConnectionValidationDto validateConfluenceConnection(ConfluenceConnectionRequest request) {
        ApplicationSettings settings = settingsRepository.get();
        settingsSecretsService.applySecretStatus(settings);
        boolean hasSavedToken = settings.getConfluence().isTokenConfigured();
        validateConfluenceConnectionRequest(request, hasSavedToken);
        String token = confluenceValidationToken(request);
        ConfluenceConnectionValidationService.ValidationResult result = confluenceConnectionValidationService.validate(
                Strings.defaultIfBlank(request.instanceUrl(), ""),
                Strings.defaultIfBlank(request.email(), ""),
                token
        );
        return new ConfluenceConnectionValidationDto(
                result.valid(),
                result.message(),
                result.liveValidationAvailable()
        );
    }

    public synchronized ConfluenceSettings saveConfluenceConnection(ConfluenceConnectionRequest request) {
        ApplicationSettings settings = settingsRepository.get();
        settingsSecretsService.applySecretStatus(settings);

        ConfluenceSettings current = settings.getConfluence();
        boolean wasConnected = current.isConnected();
        String token = Strings.defaultIfBlank(request == null ? "" : request.token(), "");
        if (request != null && request.clearToken() && token.isBlank()) {
            ConfluenceSettings cleared = new ConfluenceSettings();
            settings.setConfluence(cleared);
            removeAllConfluenceRoots();
            persistConfluenceSettings(settings, cleared, confluenceSecretRequest("", true));
            log.info("Cleared Confluence setup.");
            return currentConfluenceSettings();
        }

        validateConfluenceConnectionRequest(request, current.isTokenConfigured());
        String validationToken = token.isBlank() ? savedConfluenceToken() : token;
        ConfluenceConnectionValidationService.ValidationResult validation = confluenceConnectionValidationService.validate(
                Strings.defaultIfBlank(request.instanceUrl(), ""),
                Strings.defaultIfBlank(request.email(), ""),
                validationToken
        );
        if (!validation.valid()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, validation.message());
        }
        ConfluenceSettings next = new ConfluenceSettings();
        next.setInstanceUrl(Strings.defaultIfBlank(request.instanceUrl(), ""));
        next.setEmail(Strings.defaultIfBlank(request.email(), ""));
        next.setConnected(true);
        next.setTokenExpiresDays(current.getTokenExpiresDays() == null ? 90 : current.getTokenExpiresDays());
        if (!wasConnected) {
            // Fresh connect from disconnected: drop any stale roots so the new connection
            // starts clean (parity with Jira's reset).
            removeAllConfluenceRoots();
        }

        persistConfluenceSettings(settings, next, token.isBlank() ? null : confluenceSecretRequest(token, false));
        log.info("Saved Confluence setup for {}.", next.getInstanceUrl());
        return currentConfluenceSettings();
    }

    public synchronized ConfluenceSettings clearConfluenceConnection() {
        ApplicationSettings settings = settingsRepository.get();
        ConfluenceSettings cleared = new ConfluenceSettings();
        settings.setConfluence(cleared);
        removeAllConfluenceRoots();
        persistConfluenceSettings(settings, cleared, confluenceSecretRequest("", true));
        log.info("Cleared Confluence setup.");
        return currentConfluenceSettings();
    }

    public synchronized List<ConfluenceSpaceDto> listConfluenceSpaces() {
        return currentConfluenceSettings().getSpaces();
    }

    public synchronized List<ConfluenceSpaceDto> searchConfluenceSpaces(String query) {
        ConfluenceSettings confluence = currentConfluenceSettings();
        requireConfluenceSetup(confluence);
        return confluenceSpaceDiscoveryService.searchSpaces(
                        confluence.getInstanceUrl(),
                        confluence.getEmail(),
                        savedConfluenceToken(),
                        query,
                        CONFLUENCE_SPACE_SEARCH_LIMIT
                ).stream()
                .filter(space -> confluence.getSpaces().stream()
                        .noneMatch(configured -> configured.getKey().equalsIgnoreCase(space.getKey())))
                .toList();
    }

    public synchronized ConfluenceSpaceDto addConfluenceSpace(ConfluenceSpaceRequest request) {
        ApplicationSettings settings = settingsRepository.get();
        settingsSecretsService.applySecretStatus(settings);
        ConfluenceSettings confluence = settings.getConfluence();
        requireConfluenceSetup(confluence);

        String key = normalizeConfluenceSpaceKey(request == null ? "" : request.key());
        List<KnowledgeRoot> existing = knowledgeRootRepository.findBySource(KnowledgeSource.CONFLUENCE);
        if (existing.stream().anyMatch(root -> key.equalsIgnoreCase(ConfluenceKnowledgeRootConfig.readKey(root)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Confluence space is already configured");
        }
        if (existing.size() >= MAX_CONFLUENCE_SPACES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confluence space limit reached");
        }

        ConfluenceSpaceDto discovered = confluenceSpaceDiscoveryService.getSpace(
                confluence.getInstanceUrl(),
                confluence.getEmail(),
                savedConfluenceToken(),
                key
        );
        String spaceId = Strings.defaultIfBlank(discovered.getId(), "").trim();
        if (spaceId.isBlank()) {
            spaceId = UUID.randomUUID().toString();
        }
        KnowledgeRoot root = new KnowledgeRoot();
        root.setSource(KnowledgeSource.CONFLUENCE);
        root.setReference(ConfluenceKnowledgeReferences.spaceRootReference(confluence.getInstanceUrl(), spaceId));
        root.setDisplayName(confluenceDisplayName(discovered.getKey(), discovered.getName()));
        root.setTotalResources(discovered.getPages());
        root.setConfigJson(ConfluenceKnowledgeRootConfig.withIdentity(
                null, spaceId, discovered.getKey(), discovered.getName()));
        root = knowledgeRootRepository.save(root);
        publishSettingsUpdated();
        log.info("Added Confluence space {}.", key);
        return confluenceSpace(root);
    }

    public synchronized void removeConfluenceSpace(String spaceId) {
        ApplicationSettings settings = settingsRepository.get();
        settingsSecretsService.applySecretStatus(settings);
        requireConfluenceSetup(settings.getConfluence());
        KnowledgeRoot root = findConfluenceRoot(spaceId);
        // Stop any in-flight scan before deleting the root so the scan can't keep writing to
        // it (mirrors removeJiraProject). The scan stops between pages.
        knowledgeScanCoordinator.cancelScanAndWait(root.getId());
        KnowledgeRoot current = knowledgeRootRepository.findById(root.getId()).orElse(root);
        knowledgeRootCleanupService.removeRoot(current);
        publishSettingsUpdated();
        log.info("Removed Confluence space {}.", spaceId);
    }

    public synchronized ConfluenceSpaceDto pauseConfluenceSpace(String spaceId) {
        return setConfluenceSpacePaused(spaceId, true);
    }

    public synchronized ConfluenceSpaceDto resumeConfluenceSpace(String spaceId) {
        return setConfluenceSpacePaused(spaceId, false);
    }

    private ConfluenceSpaceDto setConfluenceSpacePaused(String spaceId, boolean paused) {
        ApplicationSettings settings = settingsRepository.get();
        settingsSecretsService.applySecretStatus(settings);
        requireConfluenceSetup(settings.getConfluence());
        KnowledgeRoot root = findConfluenceRoot(spaceId);
        root.setPaused(paused);
        root = knowledgeRootRepository.save(root);
        publishSettingsUpdated();
        return confluenceSpace(root);
    }

    private ConfluenceSettings currentConfluenceSettings() {
        ApplicationSettings settings = settingsRepository.get();
        settings.setConfluence(sanitizeConfluence(settings.getConfluence()));
        settingsSecretsService.applySecretStatus(settings);
        attachConfluenceSpaces(settings.getConfluence());
        return settings.getConfluence();
    }

    private void validateConfluenceConnectionRequest(ConfluenceConnectionRequest request, boolean hasSavedToken) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confluence connection details are required");
        }
        String instanceUrl = Strings.defaultIfBlank(request.instanceUrl(), "");
        if (instanceUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confluence instance URL is required");
        }
        if (!instanceUrl.startsWith("https://") && !instanceUrl.startsWith("http://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confluence instance URL must start with https://");
        }
        String email = Strings.defaultIfBlank(request.email(), "");
        if (email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confluence email is required");
        }
        if (!email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confluence email must be valid");
        }
        String token = Strings.defaultIfBlank(request.token(), "");
        if (token.isBlank() && !hasSavedToken) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confluence API token is required");
        }
    }

    private String confluenceValidationToken(ConfluenceConnectionRequest request) {
        String submittedToken = Strings.defaultIfBlank(request.token(), "");
        return submittedToken.isBlank() ? savedConfluenceToken() : submittedToken;
    }

    // Package-private: the scan orchestrator reads the token to drive a scan.
    String savedConfluenceToken() {
        ApplicationSettings secretValues = new ApplicationSettings();
        settingsSecretsService.applySecretValues(secretValues);
        return secretValues.getConfluence().getToken();
    }

    private void persistConfluenceSettings(
            ApplicationSettings settings,
            ConfluenceSettings confluence,
            ApplicationSettings secretRequest
    ) {
        if (secretRequest != null) {
            settingsSecretsService.saveSubmittedSecrets(secretRequest);
        }
        ConfluenceSettings sanitizedConfluence = sanitizeConfluence(confluence);
        settings.setConfluence(sanitizedConfluence);
        settingsSecretsService.clearSecretValues(settings);
        settingsSecretsService.applySecretStatus(settings);
        settingsRepository.save(settings);
        publishSettingsUpdated();
    }

    private ApplicationSettings confluenceSecretRequest(String token, boolean clearToken) {
        ApplicationSettings settings = new ApplicationSettings();
        settings.getConfluence().setToken(token);
        settings.getConfluence().setClearToken(clearToken);
        return settings;
    }

    private void requireConfluenceSetup(ConfluenceSettings confluence) {
        if (!confluence.isConnected() || !confluence.isTokenConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Save Confluence setup before managing spaces");
        }
    }

    private String confluenceDisplayName(String key, String name) {
        String trimmedKey = Strings.defaultIfBlank(key, "").trim();
        String trimmedName = Strings.defaultIfBlank(name, "").trim();
        if (trimmedKey.isBlank()) {
            return trimmedName;
        }
        if (trimmedName.isBlank()) {
            return trimmedKey;
        }
        return trimmedKey + " - " + trimmedName;
    }

    // The list of configured Confluence spaces on read, rebuilt from CONFLUENCE roots — the
    // root is the sole record, exactly like attachJiraProjects for Jira.
    private void attachConfluenceSpaces(ConfluenceSettings confluence) {
        if (confluence == null) {
            return;
        }
        confluence.setSpaces(confluence.isConnected() ? confluenceSpaces() : List.of());
    }

    private List<ConfluenceSpaceDto> confluenceSpaces() {
        return knowledgeRootRepository.findBySource(KnowledgeSource.CONFLUENCE).stream()
                .map(this::confluenceSpace)
                .toList();
    }

    // Canonical KnowledgeRoot -> Confluence-space-DTO mapper, mirroring jiraProject(root):
    // identity comes from configJson, status/pages/checked/message from the scan pipeline.
    ConfluenceSpaceDto confluenceSpace(KnowledgeRoot root) {
        ConfluenceSpaceDto space = new ConfluenceSpaceDto();
        space.setId(ConfluenceKnowledgeRootConfig.readSpaceId(root));
        space.setKey(ConfluenceKnowledgeRootConfig.readKey(root));
        space.setName(ConfluenceKnowledgeRootConfig.readName(root));
        String status = knowledgeStatus(root);
        space.setStatus(status);
        space.setPages(root.getTotalResources());
        space.setChecked(checkedLabel(root));
        space.setMessage("error".equals(status) ? root.getScanMessage() : "");
        return space;
    }

    private KnowledgeRoot findConfluenceRoot(String spaceId) {
        return knowledgeRootRepository.findBySource(KnowledgeSource.CONFLUENCE).stream()
                .filter(root -> spaceId.equals(ConfluenceKnowledgeRootConfig.readSpaceId(root)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Confluence space not found"));
    }

    private void removeAllConfluenceRoots() {
        knowledgeRootRepository.findBySource(KnowledgeSource.CONFLUENCE)
                .forEach(knowledgeRootCleanupService::removeRoot);
    }

    private String normalizeConfluenceSpaceKey(String key) {
        String normalized = Strings.defaultIfBlank(key, "").toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confluence space key is required");
        }
        return normalized;
    }

    // Persist only the connection the Settings screen needs on reload: trimmed details and
    // token expiry. Spaces are NOT persisted here — they live in CONFLUENCE roots and are
    // rebuilt by attachConfluenceSpaces on read. The secret token is handled separately by
    // SettingsSecretsService.
    private ConfluenceSettings sanitizeConfluence(ConfluenceSettings confluence) {
        ConfluenceSettings sanitized = new ConfluenceSettings();
        if (confluence == null) {
            return sanitized;
        }
        sanitized.setInstanceUrl(Strings.emptyIfNull(confluence.getInstanceUrl()).trim());
        sanitized.setEmail(Strings.emptyIfNull(confluence.getEmail()).trim());
        sanitized.setConnected(confluence.isConnected());
        sanitized.setTokenExpiresDays(confluence.getTokenExpiresDays());
        return sanitized;
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

    // Package-private: the scan orchestrator reads the token to drive a scan.
    String savedJiraToken() {
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

    private String jiraProjectRootReference(JiraSettings jira, JiraProjectDto project) {
        return JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), jiraProjectReferenceId(project));
    }

    private String jiraProjectReferenceId(JiraProjectDto project) {
        String projectId = Strings.defaultIfBlank(project.getId(), "").trim();
        return projectId.isBlank() ? project.getKey() : projectId;
    }

    private String jiraDisplayName(String key, String name) {
        String trimmedKey = Strings.defaultIfBlank(key, "").trim();
        String trimmedName = Strings.defaultIfBlank(name, "").trim();
        if (trimmedKey.isBlank()) {
            return trimmedName;
        }
        if (trimmedName.isBlank()) {
            return trimmedKey;
        }
        return trimmedKey + " - " + trimmedName;
    }

    // The list of configured Jira projects on read, rebuilt from JIRA roots — the
    // root is the sole record, exactly like attachKnowledgeFolders for local folders.
    private void attachJiraProjects(JiraSettings jira) {
        if (jira == null) {
            return;
        }
        jira.setProjects(jira.isConnected() ? jiraProjects() : List.of());
    }

    private List<JiraProjectDto> jiraProjects() {
        return knowledgeRootRepository.findBySource(KnowledgeSource.JIRA).stream()
                .map(this::jiraProject)
                .toList();
    }

    // Canonical KnowledgeRoot -> Jira-project-DTO mapper, mirroring knowledgeFolder(root):
    // identity comes from configJson, status/issues/checked/message from the scan pipeline.
    // Package-private and non-synchronized so the scan orchestrator can map a root to a
    // DTO without taking the SettingsService monitor (mirrors knowledgeFolder).
    JiraProjectDto jiraProject(KnowledgeRoot root) {
        JiraProjectDto project = new JiraProjectDto();
        project.setId(JiraKnowledgeRootConfig.readProjectId(root));
        project.setKey(JiraKnowledgeRootConfig.readKey(root));
        project.setName(JiraKnowledgeRootConfig.readName(root));
        String status = knowledgeStatus(root);
        project.setStatus(status);
        project.setIssues(root.getTotalResources());
        project.setChecked(checkedLabel(root));
        project.setMessage("error".equals(status) ? root.getScanMessage() : "");
        return project;
    }

    private KnowledgeRoot findJiraRoot(String projectId) {
        return knowledgeRootRepository.findBySource(KnowledgeSource.JIRA).stream()
                .filter(root -> projectId.equals(JiraKnowledgeRootConfig.readProjectId(root)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira project not found"));
    }

    private void removeAllJiraRoots() {
        knowledgeRootRepository.findBySource(KnowledgeSource.JIRA)
                .forEach(knowledgeRootCleanupService::removeRoot);
    }

    private String normalizeJiraProjectKey(String key) {
        String normalized = Strings.defaultIfBlank(key, "").toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira project key is required");
        }
        return normalized;
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
        KnowledgeFolderDto folder = new KnowledgeFolderDto();
        folder.setId(root.getId());
        folder.setPath(root.getReference());
        folder.setStatus(knowledgeStatus(root));
        folder.setFiles(root.getTotalResources());
        folder.setSize(root.getTotalSizeBytes() == 0
                ? ""
                : formatBytes(root.getTotalSizeBytes()));
        folder.setNextScan(nextScan(root));
        folder.setChecked(checkedLabel(root));
        folder.setMessage("error".equals(folder.getStatus()) ? root.getScanMessage() : "");
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

    // Single status precedence shared by local folders and Jira projects so the two
    // surfaces can't drift: paused beats an in-progress scan, a settled failure
    // surfaces as an error (reason in scanMessage), everything else is scanned.
    private String knowledgeStatus(KnowledgeRoot root) {
        if (root.isPaused()) {
            return "paused";
        }
        if (root.getScanStatus() == WorkStatus.IN_PROGRESS) {
            return "scanning";
        }
        if (Boolean.FALSE.equals(root.getScanSuccess())) {
            return "error";
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

    // Persist only the connection the Settings screen needs on reload: trimmed details,
    // api version, token expiry. Projects are NOT persisted here — they live in JIRA
    // roots and are rebuilt by attachJiraProjects on read. The secret token is handled
    // separately by SettingsSecretsService.
    private JiraSettings sanitizeJira(JiraSettings jira) {
        JiraSettings sanitized = new JiraSettings();
        if (jira == null) {
            return sanitized;
        }
        sanitized.setInstanceUrl(Strings.emptyIfNull(jira.getInstanceUrl()).trim());
        sanitized.setEmail(Strings.emptyIfNull(jira.getEmail()).trim());
        sanitized.setConnected(jira.isConnected());
        sanitized.setApiVersion(sanitizeJiraApiVersion(jira.getApiVersion()));
        sanitized.setTokenExpiresDays(jira.getTokenExpiresDays());
        return sanitized;
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

    private String sanitizeJiraApiVersion(String apiVersion) {
        return "2".equals(Strings.defaultIfBlank(apiVersion, "").trim()) ? "2" : "3";
    }

    private String statusMessage(ResponseStatusException exception) {
        String reason = exception.getReason();
        return reason == null || reason.isBlank() ? "Could not scan Jira project" : reason;
    }
}
