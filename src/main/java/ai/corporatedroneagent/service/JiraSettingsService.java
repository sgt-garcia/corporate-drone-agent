package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraConnectionRequest;
import ai.corporatedroneagent.dto.JiraConnectionValidationDto;
import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.dto.JiraProjectRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeReferences;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeRootConfig;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.util.Strings;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owns the Jira (API) knowledge vertical: the connection lives in settings + the secret store, the
 * chosen projects live in JIRA {@link KnowledgeRoot}s (the sole record), and indexing runs through
 * the shared knowledge pipeline. Sibling of {@link ConfluenceSettingsService}; general settings and
 * local folders stay in {@link SettingsService}, which calls {@link #sanitizeJira} / {@link
 * #attachJiraProjects} to fold Jira into the aggregate settings response.
 */
@Service
public class JiraSettingsService {

    private static final Logger log = LoggerFactory.getLogger(JiraSettingsService.class);
    private static final int MAX_JIRA_PROJECTS = 10;
    private static final int JIRA_PROJECT_SEARCH_LIMIT = 25;

    private final SettingsRepository settingsRepository;
    private final KnowledgeRootRepository knowledgeRootRepository;
    private final SettingsSecretsService settingsSecretsService;
    private final EventService eventService;
    private final KnowledgeRootCleanupService knowledgeRootCleanupService;
    private final KnowledgeScanCoordinator knowledgeScanCoordinator;
    private final KnowledgeIngestionService ingestionService;
    private final JiraConnectionValidationService jiraConnectionValidationService;
    private final JiraProjectDiscoveryService jiraProjectDiscoveryService;

    public JiraSettingsService(
            SettingsRepository settingsRepository,
            KnowledgeRootRepository knowledgeRootRepository,
            SettingsSecretsService settingsSecretsService,
            EventService eventService,
            KnowledgeRootCleanupService knowledgeRootCleanupService,
            KnowledgeScanCoordinator knowledgeScanCoordinator,
            // @Lazy breaks the construction cycle JiraSettingsService -> KnowledgeIngestionService ->
            // KnowledgeSourceRegistry -> JiraSourceAdapter -> SettingsJiraConnectionResolver ->
            // JiraSettingsService. The proxy resolves the real bean on first scan call.
            @Lazy KnowledgeIngestionService ingestionService,
            JiraConnectionValidationService jiraConnectionValidationService,
            JiraProjectDiscoveryService jiraProjectDiscoveryService
    ) {
        this.settingsRepository = settingsRepository;
        this.knowledgeRootRepository = knowledgeRootRepository;
        this.settingsSecretsService = settingsSecretsService;
        this.eventService = eventService;
        this.knowledgeRootCleanupService = knowledgeRootCleanupService;
        this.knowledgeScanCoordinator = knowledgeScanCoordinator;
        this.ingestionService = ingestionService;
        this.jiraConnectionValidationService = jiraConnectionValidationService;
        this.jiraProjectDiscoveryService = jiraProjectDiscoveryService;
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
            return clearJiraConnection();
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
                        .noneMatch(configured -> configured.key().equalsIgnoreCase(project.key())))
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
        String projectId = Strings.defaultIfBlank(discovered.id(), "").trim();
        if (projectId.isBlank()) {
            projectId = UUID.randomUUID().toString();
        }
        KnowledgeRoot root = new KnowledgeRoot();
        root.setSource(KnowledgeSource.JIRA);
        root.setReference(JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), projectId));
        root.setDisplayName(KnowledgeRootFormatting.keyAndName(discovered.key(), discovered.name()));
        root.setTotalResources(discovered.issues());
        root.setConfigJson(JiraKnowledgeRootConfig.withIdentity(
                null, projectId, discovered.key(), discovered.name()));
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

    public synchronized JiraProjectDto scanJiraProject(String projectId) {
        KnowledgeRoot root = findJiraRoot(projectId);
        ingestionService.scanInBackground(root);
        return jiraProject(root);
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

    private void validateJiraConnectionRequest(JiraConnectionRequest request, boolean hasSavedToken) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira connection details are required");
        }
        AtlassianConnectionValidation.validateConnectionDetails(
                "Jira", request.instanceUrl(), request.email(), request.token(), hasSavedToken);
    }

    private String jiraValidationToken(JiraConnectionRequest request) {
        String submittedToken = Strings.defaultIfBlank(request.token(), "");
        return submittedToken.isBlank() ? savedJiraToken() : submittedToken;
    }

    // Package-private: the connection resolver reads the token to drive a scan.
    String savedJiraToken() {
        return settingsSecretsService.jiraToken();
    }

    private void persistJiraSettings(
            ApplicationSettings settings,
            JiraSettings jira,
            ApplicationSettings secretRequest
    ) {
        // Save any submitted secret, apply the sanitized settings, then clear/restore secret
        // status, save, and publish. (Mirrors ConfluenceSettingsService#persistConfluenceSettings.)
        if (secretRequest != null) {
            settingsSecretsService.saveSubmittedSecrets(secretRequest);
        }
        settings.setJira(sanitizeJira(jira));
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

    // The list of configured Jira projects on read, rebuilt from JIRA roots — the root is the sole
    // record, exactly like local folders. Package-private so SettingsService can fold Jira into the
    // aggregate settings response.
    void attachJiraProjects(JiraSettings jira) {
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

    // Canonical KnowledgeRoot -> Jira-project-DTO mapper: identity comes from configJson,
    // status/issues/checked/message from the scan pipeline. Package-private and non-synchronized so
    // the scan orchestrator can map a root to a DTO without taking the monitor.
    JiraProjectDto jiraProject(KnowledgeRoot root) {
        String status = KnowledgeRootFormatting.status(root);
        return new JiraProjectDto(
                JiraKnowledgeRootConfig.readProjectId(root),
                JiraKnowledgeRootConfig.readKey(root),
                JiraKnowledgeRootConfig.readName(root),
                status,
                root.getTotalResources(),
                KnowledgeRootFormatting.checkedLabel(root),
                "error".equals(status) ? root.getScanMessage() : "");
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

    // Persist only the connection the Settings screen needs on reload: trimmed details, api version,
    // token expiry. Projects are NOT persisted here — they live in JIRA roots and are rebuilt by
    // attachJiraProjects on read. The secret token is handled separately by SettingsSecretsService.
    // Package-private so SettingsService can sanitize Jira inside the aggregate settings response.
    JiraSettings sanitizeJira(JiraSettings jira) {
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

    private String sanitizeJiraApiVersion(String apiVersion) {
        return "2".equals(Strings.defaultIfBlank(apiVersion, "").trim()) ? "2" : "3";
    }
}
