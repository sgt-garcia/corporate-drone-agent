package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ai.corporatedroneagent.TestDatabaseSupport;
import ai.corporatedroneagent.dto.JiraConnectionRequest;
import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.dto.JiraProjectRequest;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.security.SecretStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JiraSettingsServiceTests {

    private SettingsService settingsService;
    private InMemorySecretStore secretStore;

    @BeforeEach
    void setUp() {
        secretStore = new InMemorySecretStore();
        settingsService = serviceWithValidator(validValidator());
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

        var cleared = settingsService.clearJiraConnection();

        assertThat(cleared.isConnected()).isFalse();
        assertThat(cleared.isTokenConfigured()).isFalse();
        assertThat(cleared.getProjects()).isEmpty();
        assertThat(secretStore.get("settings.jira.token")).isEmpty();
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
    }

    @Test
    void saveJiraConnectionDoesNotMarkConnectedWhenValidationFails() {
        settingsService = serviceWithValidator(new JiraConnectionValidationService() {
            @Override
            public ValidationResult validate(String instanceUrl, String email, String token) {
                return new ValidationResult(false, "Jira rejected the email or API token.", true, 401);
            }
        });

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
    void jiraProjectLifecycleUsesExplicitEndpoints() {
        settingsService.saveJiraConnection(connection("https://example.atlassian.net", "me@example.com", "token-1234"));

        JiraProjectDto added = settingsService.addJiraProject(new JiraProjectRequest("ops"));
        assertThat(added.getId()).isEqualTo("jira-ops");
        assertThat(added.getStatus()).isEqualTo("scanned");
        assertThat(added.getIssues()).isPositive();

        JiraProjectDto paused = settingsService.pauseJiraProject(added.getId());
        assertThat(paused.getStatus()).isEqualTo("paused");

        JiraProjectDto resumed = settingsService.resumeJiraProject(added.getId());
        assertThat(resumed.getStatus()).isEqualTo("scanned");

        JiraProjectDto scanned = settingsService.scanJiraProject(added.getId());
        assertThat(scanned.getIssues()).isEqualTo(added.getIssues());
        assertThat(scanned.getChecked()).isEqualTo("just now");

        settingsService.removeJiraProject(added.getId());
        assertThat(settingsService.listJiraProjects()).isEmpty();
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

    private SettingsService serviceWithValidator(JiraConnectionValidationService validator) {
        JdbcTemplate jdbcTemplate = TestDatabaseSupport.migratedJdbcTemplate();
        return new SettingsService(
                new SettingsRepository(jdbcTemplate, new ObjectMapper().findAndRegisterModules()),
                new KnowledgeRootRepository(jdbcTemplate),
                new SettingsSecretsService(secretStore),
                mock(EventService.class),
                mock(KnowledgeRootCleanupService.class),
                new KnowledgeScanCoordinator(),
                validator
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
