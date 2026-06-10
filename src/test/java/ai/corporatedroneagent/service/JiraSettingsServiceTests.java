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
import ai.corporatedroneagent.repository.KnowledgeResourceRepository;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.security.SecretStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.JiraSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JiraSettingsServiceTests {

    private SettingsService settingsService;
    private InMemorySecretStore secretStore;
    private KnowledgeRootRepository knowledgeRootRepository;
    private SettingsRepository settingsRepository;

    @BeforeEach
    void setUp() {
        secretStore = new InMemorySecretStore();
        settingsService = serviceWith(validValidator(), fakeDiscovery());
    }

    @Test
    void saveJiraConnectionPersistsSetupAndTokenStatus() {
        var saved = settingsService.saveJiraConnection(connection("https://example.atlassian.net", "me@example.com", "token-1234"));

        assertThat(saved.isConnected()).isTrue();
        assertThat(saved.getInstanceUrl()).isEqualTo("https://example.atlassian.net");
        assertThat(saved.getEmail()).isEqualTo("me@example.com");
        assertThat(saved.isTokenConfigured()).isTrue();
        assertThat(saved.getTokenLastFour()).isEqualTo("1234");
        assertThat(saved.getToken()).isEmpty();
        assertThat(saved.getTokenExpiresDays()).isEqualTo(90);
        assertThat(secretStore.get("settings.jira.token")).contains("token-1234");
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
                return new ValidationResult(false, "Jira rejected the email or API token.", true, 401);
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

        JiraProjectDto scanned = settingsService.scanJiraProject(added.getId());
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

        when(scanService.scanProject(any(), any(), eq("token-1234"))).thenAnswer(invocation -> {
            JiraProjectDto project = invocation.getArgument(1);
            if ("DEV".equals(project.getKey())) {
                throw new JiraKnowledgeScanService.JiraScanException("Could not scan Jira project", new RuntimeException("boom"));
            }
            return new JiraKnowledgeScanService.ScanResult(5, 123);
        });

        settingsService.scanAllJiraProjects();

        verify(scanService, times(2)).scanProject(any(), any(), eq("token-1234"));
        verify(scanService).scanProject(any(), argThat(project -> "DEV".equals(project.getKey())), eq("token-1234"));
        verify(scanService).scanProject(any(), argThat(project -> "HLP".equals(project.getKey())), eq("token-1234"));
        verify(scanService, times(0)).scanProject(any(), argThat(project -> "OPS".equals(project.getKey())), any());
        assertThat(settingsService.listJiraProjects())
                .filteredOn(project -> project.getId().equals(help.getId()))
                .singleElement()
                .satisfies(project -> {
                    assertThat(project.getStatus()).isEqualTo("scanned");
                    assertThat(project.getIssues()).isEqualTo(5);
                    assertThat(project.getChecked()).isEqualTo("just now");
                });
        assertThat(settingsService.listJiraProjects())
                .filteredOn(project -> project.getId().equals(dev.getId()))
                .singleElement()
                .extracting(JiraProjectDto::getIssues)
                .isEqualTo(42L);
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
    void readingSettingsSyncsExistingSavedJiraProjectsToKnowledgeRoots() {
        ApplicationSettings settings = settingsRepository.get();
        JiraSettings jira = new JiraSettings();
        jira.setConnected(true);
        jira.setInstanceUrl("https://example.atlassian.net");
        jira.setEmail("me@example.com");
        jira.setProjects(java.util.List.of(project("10001", "DEV", "Software Development", 42)));
        settings.setJira(jira);
        settingsRepository.save(settings);
        secretStore.put("settings.jira.token", "token-1234");

        settingsService.get();

        assertThat(knowledgeRootRepository.findBySource(KnowledgeSource.JIRA))
                .singleElement()
                .satisfies(root -> {
                    assertThat(root.getReference()).isEqualTo("jira://example.atlassian.net/project/10001");
                    assertThat(root.getDisplayName()).isEqualTo("DEV - Software Development");
                });
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
        JdbcTemplate jdbcTemplate = TestDatabaseSupport.migratedJdbcTemplate();
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
                discovery,
                scanService
        );
    }

    private JiraConnectionValidationService validValidator() {
        return new JiraConnectionValidationService() {
            @Override
            public ValidationResult validate(String instanceUrl, String email, String token) {
                return new ValidationResult(true, "ok", true, 200);
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
                    int maxResults
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
            public JiraProjectDto getProject(String instanceUrl, String email, String token, String key) {
                return searchProjects(instanceUrl, email, token, key, 10).stream()
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
