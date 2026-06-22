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
                return new ValidationResult(true, "ok", 200, "2");
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
                return new ValidationResult(false, "Jira rejected the email or API token.", 401, "3");
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

        settingsService.removeJiraProject(added.getId());
        assertThat(settingsService.listJiraProjects()).isEmpty();
        assertThat(knowledgeRootRepository.findBySource(KnowledgeSource.JIRA)).isEmpty();
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
    void rejectsProjectManagementBeforeJiraSetupIsSaved() {
        assertThatThrownBy(() -> settingsService.addJiraProject(new JiraProjectRequest("DEV")))
                .hasMessageContaining("Save Jira setup before managing projects");
    }

    @Test
    void generalSettingsSaveDoesNotDisconnectJiraOrDropProjects() {
        settingsService.saveJiraConnection(connection("https://example.atlassian.net", "me@example.com", "token-1234"));
        JiraProjectDto added = settingsService.addJiraProject(new JiraProjectRequest("DEV"));

        // A general settings PUT carrying a stale/blank jira payload (connected=false)
        // must not disconnect Jira or hide its projects — the connection is owned by the
        // dedicated endpoints, and the roots are the source of truth.
        ApplicationSettings incoming = new ApplicationSettings();
        incoming.setAgentName("Renamed Agent");
        incoming.getJira().setConnected(false);
        settingsService.save(incoming);

        JiraSettings jira = settingsService.getJiraSettings();
        assertThat(jira.isConnected()).isTrue();
        assertThat(jira.getProjects()).extracting(JiraProjectDto::getId).contains(added.getId());
        assertThat(knowledgeRootRepository.findBySource(KnowledgeSource.JIRA)).hasSize(1);
        assertThat(settingsService.get().getAgentName()).isEqualTo("Renamed Agent");
    }

    @Test
    void validatesJiraConnectionShapeWithoutLiveValidation() {
        var validation = settingsService.validateJiraConnection(
                connection("https://example.atlassian.net", "me@example.com", "token-1234")
        );

        assertThat(validation.valid()).isTrue();
        assertThat(validation.apiVersion()).isEqualTo("3");
    }

    private JiraConnectionRequest connection(String instanceUrl, String email, String token) {
        return new JiraConnectionRequest(instanceUrl, email, token, false);
    }

    private SettingsService serviceWith(
            JiraConnectionValidationService validator,
            JiraProjectDiscoveryService discovery
    ) {
        jdbcTemplate = TestDatabaseSupport.migratedJdbcTemplate();
        knowledgeRootRepository = new KnowledgeRootRepository(jdbcTemplate);
        settingsRepository = new SettingsRepository(jdbcTemplate, new ObjectMapper().findAndRegisterModules());
        KnowledgeRootCleanupService cleanupService = new KnowledgeRootCleanupService(
                knowledgeRootRepository,
                new KnowledgeResourceRepository(jdbcTemplate),
                mock(KnowledgeIndexingService.class)
        );
        return new SettingsService(
                settingsRepository,
                knowledgeRootRepository,
                new SettingsSecretsService(secretStore),
                mock(EventService.class),
                cleanupService,
                new KnowledgeScanCoordinator(),
                validator,
                discovery
        );
    }

    private JiraConnectionValidationService validValidator() {
        return new JiraConnectionValidationService() {
            @Override
            public ValidationResult validate(String instanceUrl, String email, String token) {
                return new ValidationResult(true, "ok", 200, "3");
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
