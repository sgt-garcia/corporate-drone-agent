package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.corporatedroneagent.TestDatabaseSupport;
import ai.corporatedroneagent.dto.JiraConnectionRequest;
import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.dto.JiraProjectRequest;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeReferences;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeResourceRepository;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.security.SecretStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.JiraSettings;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class JiraSettingsServiceTests {

    private SettingsService settingsService;
    private InMemorySecretStore secretStore;
    private KnowledgeRootRepository knowledgeRootRepository;
    private SettingsRepository settingsRepository;
    private JdbcTemplate jdbcTemplate;
    private JiraProjectScanService jiraProjectScanService;

    @BeforeEach
    void setUp() {
        secretStore = new InMemorySecretStore();
        settingsService = serviceWith(validValidator(), fakeDiscovery());
    }

    @Test
    void settingsRowReadWriteMethodsAreSynchronized() throws NoSuchMethodException {
        assertThat(Modifier.isSynchronized(SettingsService.class.getMethod("get").getModifiers())).isTrue();
        assertThat(Modifier.isSynchronized(SettingsService.class.getMethod("getWithSecrets").getModifiers()))
                .isTrue();
        assertThat(Modifier.isSynchronized(
                SettingsService.class.getMethod("save", ApplicationSettings.class).getModifiers()
        )).isTrue();
    }

    @Test
    void saveJiraConnectionPersistsSetupAndTokenStatus() {
        var saved = settingsService.saveJiraConnection(connection("https://example.atlassian.net", "me@example.com", "token-1234"));

        assertThat(saved.isConnected()).isTrue();
        assertThat(saved.getInstanceUrl()).isEqualTo("https://example.atlassian.net");
        assertThat(saved.getEmail()).isEqualTo("me@example.com");
        assertThat(saved.getApiVersion()).isEqualTo("3");
        assertThat(saved.isTokenConfigured()).isTrue();
        assertThat(saved.getTokenLastFour()).isEqualTo("1234");
        assertThat(saved.getToken()).isEmpty();
        assertThat(saved.getTokenExpiresDays()).isEqualTo(90);
        assertThat(secretStore.get("settings.jira.token")).contains("token-1234");
    }

    @Test
    void saveJiraConnectionPersistsDetectedV2ApiVersion() {
        settingsService = serviceWith(new JiraConnectionValidationService() {
            @Override
            public ValidationResult validate(String instanceUrl, String email, String token) {
                return new ValidationResult(true, "ok", true, 200, "2");
            }
        }, fakeDiscovery());

        var saved = settingsService.saveJiraConnection(connection("https://jira.example.com", "me@example.com", "token-1234"));

        assertThat(saved.getApiVersion()).isEqualTo("2");
    }

    @Test
    void clearJiraConnectionRemovesTokenAndProjects() {
        settingsService.saveJiraConnection(connection("https://example.atlassian.net", "me@example.com", "token-1234"));
        JiraProjectDto project = settingsService.addJiraProject(new JiraProjectRequest("DEV"));

        assertThat(settingsService.listJiraProjects()).extracting(JiraProjectDto::getId).contains(project.getId());
        assertThat(knowledgeRootRepository.findBySource(KnowledgeSource.JIRA)).hasSize(1);

        var cleared = settingsService.clearJiraConnection();

        assertThat(cleared.isConnected()).isFalse();
        assertThat(cleared.isTokenConfigured()).isFalse();
        assertThat(cleared.getProjects()).isEmpty();
        assertThat(secretStore.get("settings.jira.token")).isEmpty();
        assertThat(knowledgeRootRepository.findBySource(KnowledgeSource.JIRA)).isEmpty();
    }

    @Test
    void clearTokenOnSaveClearsJiraSetup() {
        settingsService.saveJiraConnection(connection("https://example.atlassian.net", "me@example.com", "token-1234"));
        settingsService.addJiraProject(new JiraProjectRequest("DEV"));

        var cleared = settingsService.saveJiraConnection(
                new JiraConnectionRequest("https://example.atlassian.net", "me@example.com", "", true)
        );

        assertThat(cleared.isConnected()).isFalse();
        assertThat(cleared.isTokenConfigured()).isFalse();
        assertThat(cleared.getProjects()).isEmpty();
        assertThat(secretStore.get("settings.jira.token")).isEmpty();
        assertThat(knowledgeRootRepository.findBySource(KnowledgeSource.JIRA)).isEmpty();
    }

    @Test
    void saveJiraConnectionDoesNotMarkConnectedWhenValidationFails() {
        settingsService = serviceWith(new JiraConnectionValidationService() {
            @Override
            public ValidationResult validate(String instanceUrl, String email, String token) {
                return new ValidationResult(false, "Jira rejected the email or API token.", true, 401, "3");
            }
        }, fakeDiscovery());

        assertThatThrownBy(() -> settingsService.saveJiraConnection(
                connection("https://example.atlassian.net", "me@example.com", "wrong-token")
        )).hasMessageContaining("Jira rejected the email or API token");
        assertThat(settingsService.getJiraSettings().isConnected()).isFalse();
        assertThat(secretStore.get("settings.jira.token")).isEmpty();
    }

    @Test
    void searchJiraProjectsFiltersConfiguredProjects() {
        settingsService.saveJiraConnection(connection("https://example.atlassian.net", "me@example.com", "token-1234"));

        assertThat(settingsService.searchJiraProjects("dev"))
                .extracting(JiraProjectDto::getKey)
                .containsExactly("DEV");

        settingsService.addJiraProject(new JiraProjectRequest("DEV"));

        assertThat(settingsService.searchJiraProjects("dev")).isEmpty();
    }

    @Test
    void addingJiraProjectCreatesStableKnowledgeRoot() {
        settingsService.saveJiraConnection(connection("https://Example.atlassian.net/", "me@example.com", "token-1234"));

        JiraProjectDto added = settingsService.addJiraProject(new JiraProjectRequest("DEV"));

        assertThat(knowledgeRootRepository.findBySource(KnowledgeSource.JIRA))
                .singleElement()
                .satisfies(root -> {
                    assertThat(root.getReference()).isEqualTo("jira://example.atlassian.net/project/10001");
                    assertThat(root.getDisplayName()).isEqualTo("DEV - Software Development");
                    assertThat(root.getTotalResources()).isEqualTo(42);
                    assertThat(root.isPaused()).isFalse();
                });
        assertThat(added.getId()).isEqualTo("10001");
        assertThat(JiraKnowledgeReferences.issueResourceReference(
                "https://Example.atlassian.net/",
                "12345"
        )).isEqualTo("jira://example.atlassian.net/issue/12345");
    }

    @Test
    void jiraProjectLifecycleUsesExplicitEndpoints() {
        settingsService.saveJiraConnection(connection("https://example.atlassian.net", "me@example.com", "token-1234"));

        JiraProjectDto added = settingsService.addJiraProject(new JiraProjectRequest("ops"));
        assertThat(added.getId()).isEqualTo("10002");
        assertThat(added.getStatus()).isEqualTo("scanned");
        assertThat(added.getIssues()).isEqualTo(17);
        assertThat(knowledgeRootRepository.findBySource(KnowledgeSource.JIRA))
                .extracting(KnowledgeRoot::getReference)
                .containsExactly("jira://example.atlassian.net/project/10002");

        JiraProjectDto paused = settingsService.pauseJiraProject(added.getId());
        assertThat(paused.getStatus()).isEqualTo("paused");
        assertThat(knowledgeRootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                "jira://example.atlassian.net/project/10002"
        )).get().extracting(KnowledgeRoot::isPaused).isEqualTo(true);

        JiraProjectDto resumed = settingsService.resumeJiraProject(added.getId());
        assertThat(resumed.getStatus()).isEqualTo("scanned");
        assertThat(knowledgeRootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                "jira://example.atlassian.net/project/10002"
        )).get().extracting(KnowledgeRoot::isPaused).isEqualTo(false);

        JiraProjectDto scanned = jiraProjectScanService.scanProject(added.getId());
        assertThat(scanned.getIssues()).isEqualTo(added.getIssues());
        assertThat(scanned.getChecked()).isEqualTo("just now");

        settingsService.removeJiraProject(added.getId());
        assertThat(settingsService.listJiraProjects()).isEmpty();
        assertThat(knowledgeRootRepository.findBySource(KnowledgeSource.JIRA)).isEmpty();
    }

    @Test
    void scheduledJiraScanSkipsPausedProjectsAndContinuesAfterProjectFailure() {
        JiraKnowledgeScanService scanService = mock(JiraKnowledgeScanService.class);
        settingsService = serviceWith(validValidator(), fakeDiscovery(), scanService);
        settingsService.saveJiraConnection(connection("https://example.atlassian.net", "me@example.com", "token-1234"));
        JiraProjectDto dev = settingsService.addJiraProject(new JiraProjectRequest("DEV"));
        JiraProjectDto ops = settingsService.addJiraProject(new JiraProjectRequest("OPS"));
        JiraProjectDto help = settingsService.addJiraProject(new JiraProjectRequest("HLP"));
        settingsService.pauseJiraProject(ops.getId());

        when(scanService.scanScheduledProject(any(), any(), eq("token-1234"), any(), any())).thenAnswer(invocation -> {
            JiraProjectDto project = invocation.getArgument(1);
            if ("DEV".equals(project.getKey())) {
                throw new JiraKnowledgeScanService.JiraScanException("Could not scan Jira project", new RuntimeException("boom"));
            }
            return new JiraKnowledgeScanService.ScanResult(5, 123);
        });

        jiraProjectScanService.scanAllProjects();

        verify(scanService, times(2)).scanScheduledProject(any(), any(), eq("token-1234"), any(), any());
        verify(scanService).scanScheduledProject(any(), argThat(project -> "DEV".equals(project.getKey())), eq("token-1234"), any(), any());
        verify(scanService).scanScheduledProject(any(), argThat(project -> "HLP".equals(project.getKey())), eq("token-1234"), any(), any());
        verify(scanService, times(0)).scanScheduledProject(any(), argThat(project -> "OPS".equals(project.getKey())), any(), any(), any());
        // OPS was paused and skipped; HLP scanned past DEV's failure; DEV's failure was
        // recorded on its root, so its derived status is "error". Statuses now derive
        // from the KnowledgeRoot rather than the ScanResult the (mocked) scanner returns.
        assertThat(settingsService.listJiraProjects())
                .filteredOn(project -> project.getId().equals(ops.getId()))
                .singleElement()
                .extracting(JiraProjectDto::getStatus)
                .isEqualTo("paused");
        assertThat(settingsService.listJiraProjects())
                .filteredOn(project -> project.getId().equals(help.getId()))
                .singleElement()
                .extracting(JiraProjectDto::getStatus)
                .isEqualTo("scanned");
        assertThat(settingsService.listJiraProjects())
                .filteredOn(project -> project.getId().equals(dev.getId()))
                .singleElement()
                .satisfies(project -> {
                    assertThat(project.getStatus()).isEqualTo("error");
                    assertThat(project.getIssues()).isEqualTo(42L);
                });
    }

    @Test
    void scanJiraProjectPersistsErrorStatusAndPreservesResponseStatusException() {
        JiraKnowledgeScanService scanService = mock(JiraKnowledgeScanService.class);
        settingsService = serviceWith(validValidator(), fakeDiscovery(), scanService);
        settingsService.saveJiraConnection(connection("https://example.atlassian.net", "me@example.com", "token-1234"));
        JiraProjectDto dev = settingsService.addJiraProject(new JiraProjectRequest("DEV"));
        when(scanService.scanProject(any(), any(), eq("token-1234"), any(), any())).thenThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Jira does not allow this account to read issues")
        );

        assertThatThrownBy(() -> jiraProjectScanService.scanProject(dev.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Jira does not allow this account to read issues");

        assertThat(settingsService.listJiraProjects())
                .singleElement()
                .satisfies(project -> {
                    assertThat(project.getStatus()).isEqualTo("error");
                    assertThat(project.getMessage()).isEqualTo("Jira does not allow this account to read issues");
                    assertThat(project.getIssues()).isEqualTo(42);
                });
    }

    @Test
    void jiraProjectStatusDerivesScanningAndPausedFromKnowledgeRoot() {
        settingsService.saveJiraConnection(connection("https://example.atlassian.net", "me@example.com", "token-1234"));
        settingsService.addJiraProject(new JiraProjectRequest("DEV"));
        KnowledgeRoot root = knowledgeRootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                "jira://example.atlassian.net/project/10001"
        ).orElseThrow();

        // An in-progress scan surfaces as "scanning", derived from the root rather than
        // any stored project status — this is what lets the settings screen show a live
        // scan without a client-side animation.
        root.setScanStatus(WorkStatus.IN_PROGRESS);
        root.setScanSuccess(null);
        knowledgeRootRepository.save(root);
        assertThat(settingsService.listJiraProjects())
                .singleElement()
                .extracting(JiraProjectDto::getStatus)
                .isEqualTo("scanning");

        // A pause that lands mid-scan must win over the in-progress scan, so the pause
        // button sticks instead of the row snapping back to "scanning".
        root.setPaused(true);
        knowledgeRootRepository.save(root);
        assertThat(settingsService.listJiraProjects())
                .singleElement()
                .extracting(JiraProjectDto::getStatus)
                .isEqualTo("paused");
    }

    @Test
    void syncingJiraProjectsDoesNotTouchLocalFolderRoots() {
        KnowledgeRoot localRoot = new KnowledgeRoot();
        localRoot.setSource(KnowledgeSource.LOCAL_FOLDER);
        localRoot.setReference("C:\\Data");
        localRoot.setDisplayName("Data");
        localRoot = knowledgeRootRepository.save(localRoot);

        settingsService.saveJiraConnection(connection("https://example.atlassian.net", "me@example.com", "token-1234"));
        settingsService.addJiraProject(new JiraProjectRequest("DEV"));
        settingsService.clearJiraConnection();

        assertThat(knowledgeRootRepository.findByIdAndSource(localRoot.getId(), KnowledgeSource.LOCAL_FOLDER))
                .isPresent();
    }

    @Test
    void readingSettingsMigratesStoredJiraProjectsToKnowledgeRoots() {
        JiraSettings jira = new JiraSettings();
        jira.setConnected(true);
        jira.setInstanceUrl("https://example.atlassian.net");
        jira.setEmail("me@example.com");
        jira.setProjects(java.util.List.of(project("10001", "DEV", "Software Development", 42)));
        seedLegacyJiraProjects(jira);

        settingsService.getJiraSettings();

        assertThat(knowledgeRootRepository.findBySource(KnowledgeSource.JIRA))
                .singleElement()
                .satisfies(root -> {
                    assertThat(root.getReference()).isEqualTo("jira://example.atlassian.net/project/10001");
                    assertThat(root.getDisplayName()).isEqualTo("DEV - Software Development");
                    assertThat(root.getTotalResources()).isEqualTo(42);
                });
        // The stored list is cleared once migrated.
        assertThat(settingsRepository.get().getJira().getProjects()).isEmpty();
    }

    @Test
    void readingSettingsDoesNotResetActiveJiraKnowledgeRoot() {
        Instant startedAt = Instant.parse("2026-06-13T12:00:00Z");
        KnowledgeRoot root = new KnowledgeRoot();
        root.setSource(KnowledgeSource.JIRA);
        root.setReference("jira://example.atlassian.net/project/10001");
        root.setDisplayName("DEV - Software Development");
        root.setScanStatus(WorkStatus.IN_PROGRESS);
        root.setScanSuccess(null);
        root.setScanMessage("");
        root.setScanStartedAt(startedAt);
        root.setConfigJson("{\"kept\":\"value\"}");
        root = knowledgeRootRepository.save(root);

        JiraSettings jira = new JiraSettings();
        jira.setConnected(true);
        jira.setInstanceUrl("https://example.atlassian.net");
        jira.setEmail("me@example.com");
        jira.setProjects(java.util.List.of(project("10001", "DEV", "Software Development", 42)));
        seedLegacyJiraProjects(jira);

        settingsService.get();

        // Migration writes the project identity into the existing root's configJson but
        // must not touch its in-flight scan state, nor clobber unrelated config keys.
        assertThat(knowledgeRootRepository.findByIdAndSource(root.getId(), KnowledgeSource.JIRA))
                .get()
                .satisfies(savedRoot -> {
                    assertThat(savedRoot.getScanStatus()).isEqualTo(WorkStatus.IN_PROGRESS);
                    assertThat(savedRoot.getScanStartedAt()).isEqualTo(startedAt);
                    assertThat(savedRoot.getScanFinishedAt()).isNull();
                    assertThat(savedRoot.getScanSuccess()).isNull();
                    assertThat(savedRoot.getConfigJson()).contains("\"kept\":\"value\"");
                    assertThat(savedRoot.getConfigJson()).contains("\"jiraProjectKey\":\"DEV\"");
                });
    }

    @Test
    void readingJiraSettingsMigratesAndDedupesPersistedProjects() {
        JiraSettings jira = new JiraSettings();
        jira.setConnected(true);
        jira.setInstanceUrl(" https://example.atlassian.net/ ");
        jira.setEmail(" me@example.com ");
        jira.setTokenExpiresDays(30);

        JiraProjectDto dev = project("", " dev ", "Software Development", 42);
        dev.setStatus("scanning");
        JiraProjectDto duplicateDev = project("10099", "DEV", "Duplicate Development", 99);
        JiraProjectDto invalid = project("10003", " ", "Missing Key", 1);
        JiraProjectDto ops = project("10002", "OPS", "Operations", 17);
        ops.setStatus("paused");
        JiraProjectDto support = project("10004", "SUP", "Support", 8);
        support.setStatus("error");
        support.setMessage("Could not scan Jira project");
        jira.setProjects(java.util.List.of(dev, duplicateDev, invalid, ops, support));
        seedLegacyJiraProjects(jira);
        secretStore.put("settings.jira.token", "token-1234");

        JiraSettings sanitized = settingsService.getJiraSettings();

        assertThat(sanitized.getInstanceUrl()).isEqualTo("https://example.atlassian.net/");
        assertThat(sanitized.getEmail()).isEqualTo("me@example.com");
        assertThat(sanitized.getTokenExpiresDays()).isEqualTo(30);
        assertThat(sanitized.getApiVersion()).isEqualTo("3");
        assertThat(sanitized.isTokenConfigured()).isTrue();
        // Blank-key skipped, DEV deduped against "dev" (case-insensitive).
        assertThat(sanitized.getProjects())
                .extracting(JiraProjectDto::getKey)
                .containsExactlyInAnyOrder("dev", "OPS", "SUP");
        assertThat(sanitized.getProjects())
                .filteredOn(project -> "dev".equals(project.getKey()))
                .singleElement()
                .satisfies(project -> {
                    assertThat(project.getId()).isNotBlank();
                    assertThat(project.getStatus()).isEqualTo("scanned");
                    assertThat(project.getIssues()).isEqualTo(42);
                });
        assertThat(sanitized.getProjects())
                .filteredOn(project -> "OPS".equals(project.getKey()))
                .singleElement()
                .extracting(JiraProjectDto::getStatus)
                .isEqualTo("paused");
        assertThat(sanitized.getProjects())
                .filteredOn(project -> "SUP".equals(project.getKey()))
                .singleElement()
                .satisfies(project -> {
                    assertThat(project.getStatus()).isEqualTo("error");
                    assertThat(project.getMessage()).isEqualTo("Could not scan Jira project");
                });
        assertThat(knowledgeRootRepository.findBySource(KnowledgeSource.JIRA)).hasSize(3);
    }

    @Test
    void rejectsProjectManagementBeforeJiraSetupIsSaved() {
        assertThatThrownBy(() -> settingsService.addJiraProject(new JiraProjectRequest("DEV")))
                .hasMessageContaining("Save Jira setup before managing projects");
    }

    @Test
    void validatesJiraConnectionShapeWithoutLiveValidation() {
        var validation = settingsService.validateJiraConnection(
                connection("https://example.atlassian.net", "me@example.com", "token-1234")
        );

        assertThat(validation.valid()).isTrue();
        assertThat(validation.liveValidationAvailable()).isTrue();
        assertThat(validation.apiVersion()).isEqualTo("3");
    }

    private JiraConnectionRequest connection(String instanceUrl, String email, String token) {
        return new JiraConnectionRequest(instanceUrl, email, token, false);
    }

    private SettingsService serviceWith(
            JiraConnectionValidationService validator,
            JiraProjectDiscoveryService discovery
    ) {
        return serviceWith(validator, discovery, null);
    }

    private SettingsService serviceWith(
            JiraConnectionValidationService validator,
            JiraProjectDiscoveryService discovery,
            JiraKnowledgeScanService scanService
    ) {
        jdbcTemplate = TestDatabaseSupport.migratedJdbcTemplate();
        knowledgeRootRepository = new KnowledgeRootRepository(jdbcTemplate);
        settingsRepository = new SettingsRepository(jdbcTemplate, new ObjectMapper().findAndRegisterModules());
        KnowledgeRootCleanupService cleanupService = new KnowledgeRootCleanupService(
                knowledgeRootRepository,
                new KnowledgeResourceRepository(jdbcTemplate),
                mock(KnowledgeIndexingService.class)
        );
        KnowledgeScanCoordinator coordinator = new KnowledgeScanCoordinator();
        EventService eventService = mock(EventService.class);
        SettingsService service = new SettingsService(
                settingsRepository,
                knowledgeRootRepository,
                new SettingsSecretsService(secretStore),
                eventService,
                cleanupService,
                coordinator,
                validator,
                discovery
        );
        jiraProjectScanService = new JiraProjectScanService(
                service,
                knowledgeRootRepository,
                eventService,
                coordinator,
                scanService
        );
        return service;
    }

    private JiraConnectionValidationService validValidator() {
        return new JiraConnectionValidationService() {
            @Override
            public ValidationResult validate(String instanceUrl, String email, String token) {
                return new ValidationResult(true, "ok", true, 200, "3");
            }
        };
    }

    private JiraProjectDiscoveryService fakeDiscovery() {
        return new JiraProjectDiscoveryService(new ObjectMapper()) {
            @Override
            public java.util.List<JiraProjectDto> searchProjects(
                    String instanceUrl,
                    String email,
                    String token,
                    String query,
                    int maxResults,
                    String apiVersion
            ) {
                String normalizedQuery = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
                return java.util.List.of(
                                project("10001", "DEV", "Software Development", 42),
                                project("10002", "OPS", "Operations", 17),
                                project("10003", "HLP", "Help Desk", 3)
                        ).stream()
                        .filter(project -> normalizedQuery.isBlank()
                                || (project.getKey() + " " + project.getName())
                                .toLowerCase(java.util.Locale.ROOT)
                                .contains(normalizedQuery))
                        .limit(maxResults)
                        .toList();
            }

            @Override
            public JiraProjectDto getProject(String instanceUrl, String email, String token, String key, String apiVersion) {
                return searchProjects(instanceUrl, email, token, key, 10, apiVersion).stream()
                        .filter(project -> project.getKey().equalsIgnoreCase(key))
                        .findFirst()
                        .orElseThrow();
            }
        };
    }

    // Seed a legacy settings_json that still carries jira.projects, simulating an
    // install from before projects moved to knowledge roots. Production
    // SettingsRepository strips jira.projects on write, so we insert raw JSON here.
    private void seedLegacyJiraProjects(JiraSettings jira) {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setJira(jira);
        try {
            String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(settings);
            jdbcTemplate.update(
                    "MERGE INTO app_settings (id, settings_json, updated_at) KEY(id) VALUES (1, ?, ?)",
                    json,
                    java.sql.Timestamp.from(Instant.now()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JiraProjectDto project(String id, String key, String name, long issues) {
        JiraProjectDto project = new JiraProjectDto();
        project.setId(id);
        project.setKey(key);
        project.setName(name);
        project.setIssues(issues);
        project.setChecked("just now");
        return project;
    }

    private static class InMemorySecretStore implements SecretStore {

        private final Map<String, String> secrets = new HashMap<>();

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(secrets.get(key));
        }

        @Override
        public void put(String key, String secret) {
            secrets.put(key, secret);
        }

        @Override
        public void delete(String key) {
            secrets.remove(key);
        }
    }
}
