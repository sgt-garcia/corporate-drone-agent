package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ai.corporatedroneagent.TestDatabaseSupport;
import ai.corporatedroneagent.dto.KnowledgeFolderDto;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.security.SecretStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeFolderSettingsServiceTests {

    @TempDir
    private Path root;
    private SettingsRepository settingsRepository;
    private EventService eventService;
    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = TestDatabaseSupport.migratedJdbcTemplate();

        settingsRepository = new SettingsRepository(jdbcTemplate, new ObjectMapper().findAndRegisterModules());
        eventService = mock(EventService.class);
        KnowledgeRootRepository rootRepository = new KnowledgeRootRepository(jdbcTemplate);
        SettingsSecretsService secretsService = new SettingsSecretsService(new InMemorySecretStore());
        KnowledgeRootCleanupService cleanupService = mock(KnowledgeRootCleanupService.class);
        KnowledgeScanCoordinator coordinator = new KnowledgeScanCoordinator();
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        // Real Jira/Confluence services (never connected here) so get()/save() can fold their
        // sanitized, empty verticals into the aggregate settings without stubbing.
        JiraSettingsService jiraSettingsService = new JiraSettingsService(
                settingsRepository, rootRepository, secretsService, eventService, cleanupService,
                coordinator, ingestionService, new JiraConnectionValidationService(),
                new JiraProjectDiscoveryService(new ObjectMapper()));
        ConfluenceSettingsService confluenceSettingsService = new ConfluenceSettingsService(
                settingsRepository, rootRepository, secretsService, eventService, cleanupService,
                coordinator, ingestionService, new ConfluenceConnectionValidationService(),
                new ConfluenceSpaceDiscoveryService(new ObjectMapper()));
        settingsService = new SettingsService(
                settingsRepository,
                rootRepository,
                secretsService,
                eventService,
                cleanupService,
                coordinator,
                ingestionService,
                jiraSettingsService,
                confluenceSettingsService
        );
    }

    @Test
    void addingLocalFolderPersistsItInApplicationSettings() throws IOException {
        Path workFolder = Files.createDirectory(root.resolve("Work"));

        KnowledgeFolderDto folder = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(workFolder.toString()));

        assertThat(folder.id()).isNotNull();
        assertThat(folder.path()).isEqualTo(workFolder.toString());
        assertThat(folder.status()).isEqualTo("scanned");
        assertThat(settingsService.listKnowledgeFolders())
                .extracting(KnowledgeFolderDto::path)
                .containsExactly(workFolder.toString());
        verify(eventService).publish(eq("settings-updated"));
    }

    @Test
    void rejectsMissingLocalFolder() {
        Path missingFolder = root.resolve("Missing");

        assertThatThrownBy(() -> settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(missingFolder.toString())))
                .hasMessageContaining("Folder path must be an existing folder");

        assertThat(settingsService.listKnowledgeFolders()).isEmpty();
    }

    @Test
    void rejectsChildFolderWhenParentIsConfigured() throws IOException {
        Path parent = Files.createDirectory(root.resolve("Parent"));
        Path child = Files.createDirectory(parent.resolve("Child"));
        settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(parent.toString()));

        assertThatThrownBy(() -> settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(child.toString())))
                .hasMessageContaining("Folders must not be nested inside each other");

        assertThat(settingsService.listKnowledgeFolders())
                .extracting(KnowledgeFolderDto::path)
                .containsExactly(parent.toString());
    }

    @Test
    void rejectsParentFolderWhenChildIsConfigured() throws IOException {
        Path parent = Files.createDirectory(root.resolve("Parent"));
        Path child = Files.createDirectory(parent.resolve("Child"));
        settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(child.toString()));

        assertThatThrownBy(() -> settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(parent.toString())))
                .hasMessageContaining("Folders must not be nested inside each other");

        assertThat(settingsService.listKnowledgeFolders())
                .extracting(KnowledgeFolderDto::path)
                .containsExactly(child.toString());
    }

    @Test
    void genericSettingsSaveDoesNotReplaceKnowledgeFolders() throws IOException {
        KnowledgeFolderDto folder = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(existingFolderPath()));
        ApplicationSettings submittedSettings = settingsService.get();
        submittedSettings.setKnowledgeFolders(List.of());
        submittedSettings.setAgentName("Renamed Agent");

        ApplicationSettings savedSettings = settingsService.save(submittedSettings);

        assertThat(savedSettings.getAgentName()).isEqualTo("Renamed Agent");
        assertThat(savedSettings.getKnowledgeFolders())
                .extracting(KnowledgeFolderDto::id)
                .containsExactly(folder.id());
    }

    @Test
    void removingLocalFolderPersistsTheRemoval() throws IOException {
        KnowledgeFolderDto folder = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(existingFolderPath()));

        settingsService.removeKnowledgeFolder(folder.id());

        assertThat(settingsService.listKnowledgeFolders()).isEmpty();
    }

    @Test
    void pauseAndResumeScanningPersistStatus() throws IOException {
        KnowledgeFolderDto folder = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(existingFolderPath()));

        KnowledgeFolderDto paused = settingsService.pauseKnowledgeFolder(folder.id());
        assertThat(paused.status()).isEqualTo("paused");
        assertThat(settingsService.listKnowledgeFolders().getFirst().status()).isEqualTo("paused");

        KnowledgeFolderDto resumed = settingsService.resumeKnowledgeFolder(folder.id());
        assertThat(resumed.status()).isEqualTo("scanned");
        assertThat(settingsService.listKnowledgeFolders().getFirst().status()).isEqualTo("scanned");
    }

    @Test
    void formatsRelativeScanFreshness() {
        Instant now = Instant.parse("2026-06-10T12:00:00Z");

        assertThat(KnowledgeRootFormatting.relativeTime(now.minusSeconds(10), now)).isEqualTo("just now");
        assertThat(KnowledgeRootFormatting.relativeTime(now.minusSeconds(120), now)).isEqualTo("2 min ago");
        assertThat(KnowledgeRootFormatting.relativeTime(now.minus(Duration.ofHours(1)), now)).isEqualTo("1 hour ago");
        assertThat(KnowledgeRootFormatting.relativeTime(now.minus(Duration.ofHours(5)), now)).isEqualTo("5 hours ago");
        assertThat(KnowledgeRootFormatting.relativeTime(now.minus(Duration.ofDays(3)), now)).isEqualTo("3 days ago");
        assertThat(KnowledgeRootFormatting.relativeTime(now.plusSeconds(30), now)).isEqualTo("just now");
    }

    private String existingFolderPath() throws IOException {
        return Files.createDirectory(root.resolve("folder-" + UUID.randomUUID())).toString();
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
