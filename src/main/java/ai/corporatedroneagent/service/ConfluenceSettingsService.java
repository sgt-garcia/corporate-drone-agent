package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ConfluenceConnectionRequest;
import ai.corporatedroneagent.dto.ConfluenceConnectionValidationDto;
import ai.corporatedroneagent.dto.ConfluenceSpaceDto;
import ai.corporatedroneagent.dto.ConfluenceSpaceRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.ConfluenceSettings;
import ai.corporatedroneagent.model.knowledge.ConfluenceKnowledgeReferences;
import ai.corporatedroneagent.model.knowledge.ConfluenceKnowledgeRootConfig;
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
 * Owns the Confluence (API) knowledge vertical — sibling of {@link JiraSettingsService}. Same
 * connect/scan archetype: the connection lives in settings + the secret store, the chosen spaces
 * live in CONFLUENCE {@link KnowledgeRoot}s (the sole record), and indexing runs through the shared
 * knowledge pipeline. {@link SettingsService} calls {@link #sanitizeConfluence} / {@link
 * #attachConfluenceSpaces} to fold Confluence into the aggregate settings response.
 */
@Service
public class ConfluenceSettingsService {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceSettingsService.class);
    private static final int MAX_CONFLUENCE_SPACES = 10;
    private static final int CONFLUENCE_SPACE_SEARCH_LIMIT = 25;

    private final SettingsRepository settingsRepository;
    private final KnowledgeRootRepository knowledgeRootRepository;
    private final SettingsSecretsService settingsSecretsService;
    private final EventService eventService;
    private final KnowledgeRootCleanupService knowledgeRootCleanupService;
    private final KnowledgeScanCoordinator knowledgeScanCoordinator;
    private final KnowledgeIngestionService ingestionService;
    private final ConfluenceConnectionValidationService confluenceConnectionValidationService;
    private final ConfluenceSpaceDiscoveryService confluenceSpaceDiscoveryService;

    public ConfluenceSettingsService(
            SettingsRepository settingsRepository,
            KnowledgeRootRepository knowledgeRootRepository,
            SettingsSecretsService settingsSecretsService,
            EventService eventService,
            KnowledgeRootCleanupService knowledgeRootCleanupService,
            KnowledgeScanCoordinator knowledgeScanCoordinator,
            // @Lazy breaks the construction cycle ConfluenceSettingsService -> KnowledgeIngestionService
            // -> KnowledgeSourceRegistry -> ConfluenceSourceAdapter -> SettingsConfluenceConnectionResolver
            // -> ConfluenceSettingsService. The proxy resolves the real bean on first scan call.
            @Lazy KnowledgeIngestionService ingestionService,
            ConfluenceConnectionValidationService confluenceConnectionValidationService,
            ConfluenceSpaceDiscoveryService confluenceSpaceDiscoveryService
    ) {
        this.settingsRepository = settingsRepository;
        this.knowledgeRootRepository = knowledgeRootRepository;
        this.settingsSecretsService = settingsSecretsService;
        this.eventService = eventService;
        this.knowledgeRootCleanupService = knowledgeRootCleanupService;
        this.knowledgeScanCoordinator = knowledgeScanCoordinator;
        this.ingestionService = ingestionService;
        this.confluenceConnectionValidationService = confluenceConnectionValidationService;
        this.confluenceSpaceDiscoveryService = confluenceSpaceDiscoveryService;
    }

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
                result.message()
        );
    }

    public synchronized ConfluenceSettings saveConfluenceConnection(ConfluenceConnectionRequest request) {
        ApplicationSettings settings = settingsRepository.get();
        settingsSecretsService.applySecretStatus(settings);

        ConfluenceSettings current = settings.getConfluence();
        boolean wasConnected = current.isConnected();
        String token = Strings.defaultIfBlank(request == null ? "" : request.token(), "");
        if (request != null && request.clearToken() && token.isBlank()) {
            return clearConfluenceConnection();
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
                        .noneMatch(configured -> configured.key().equalsIgnoreCase(space.key())))
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
        String spaceId = Strings.defaultIfBlank(discovered.id(), "").trim();
        if (spaceId.isBlank()) {
            spaceId = UUID.randomUUID().toString();
        }
        KnowledgeRoot root = new KnowledgeRoot();
        root.setSource(KnowledgeSource.CONFLUENCE);
        root.setReference(ConfluenceKnowledgeReferences.spaceRootReference(confluence.getInstanceUrl(), spaceId));
        root.setDisplayName(KnowledgeRootFormatting.keyAndName(discovered.key(), discovered.name()));
        root.setTotalResources(discovered.pages());
        root.setConfigJson(ConfluenceKnowledgeRootConfig.withIdentity(
                null, spaceId, discovered.key(), discovered.name()));
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

    public synchronized ConfluenceSpaceDto scanConfluenceSpace(String spaceId) {
        KnowledgeRoot root = findConfluenceRoot(spaceId);
        ingestionService.scanInBackground(root);
        return confluenceSpace(root);
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
        AtlassianConnectionValidation.validateConnectionDetails(
                "Confluence", request.instanceUrl(), request.email(), request.token(), hasSavedToken);
    }

    private String confluenceValidationToken(ConfluenceConnectionRequest request) {
        String submittedToken = Strings.defaultIfBlank(request.token(), "");
        return submittedToken.isBlank() ? savedConfluenceToken() : submittedToken;
    }

    // Package-private: the connection resolver reads the token to drive a scan.
    String savedConfluenceToken() {
        return settingsSecretsService.confluenceToken();
    }

    private void persistConfluenceSettings(
            ApplicationSettings settings,
            ConfluenceSettings confluence,
            ApplicationSettings secretRequest
    ) {
        // Save any submitted secret, apply the sanitized settings, then clear/restore secret
        // status, save, and publish. (Mirrors JiraSettingsService#persistJiraSettings.)
        if (secretRequest != null) {
            settingsSecretsService.saveSubmittedSecrets(secretRequest);
        }
        settings.setConfluence(sanitizeConfluence(confluence));
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

    // The list of configured Confluence spaces on read, rebuilt from CONFLUENCE roots — the root is
    // the sole record, exactly like attachJiraProjects for Jira. Package-private so SettingsService
    // can fold Confluence into the aggregate settings response.
    void attachConfluenceSpaces(ConfluenceSettings confluence) {
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

    // Canonical KnowledgeRoot -> Confluence-space-DTO mapper, mirroring jiraProject(root): identity
    // comes from configJson, status/pages/checked/message from the scan pipeline. Package-private
    // and non-synchronized so the scan orchestrator can map a root to a DTO without the monitor.
    ConfluenceSpaceDto confluenceSpace(KnowledgeRoot root) {
        String status = KnowledgeRootFormatting.status(root);
        return new ConfluenceSpaceDto(
                ConfluenceKnowledgeRootConfig.readSpaceId(root),
                ConfluenceKnowledgeRootConfig.readKey(root),
                ConfluenceKnowledgeRootConfig.readName(root),
                status,
                root.getTotalResources(),
                KnowledgeRootFormatting.checkedLabel(root),
                "error".equals(status) ? root.getScanMessage() : "");
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

    private void publishSettingsUpdated() {
        eventService.publish("settings-updated");
    }

    // Persist only the connection the Settings screen needs on reload: trimmed details and token
    // expiry. Spaces are NOT persisted here — they live in CONFLUENCE roots and are rebuilt by
    // attachConfluenceSpaces on read. The secret token is handled separately by SettingsSecretsService.
    // Package-private so SettingsService can sanitize Confluence inside the aggregate settings response.
    ConfluenceSettings sanitizeConfluence(ConfluenceSettings confluence) {
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
}
