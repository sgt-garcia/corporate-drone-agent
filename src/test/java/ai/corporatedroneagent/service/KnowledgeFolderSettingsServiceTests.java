package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.security.SecretStore;
import ai.corporatedroneagent.util.JsonFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeFolderSettingsServiceTests {

    private SettingsRepository settingsRepository;
    private EventService eventService;
    private SettingsService settingsService;

    @BeforeEach
    void setUp(@TempDir Path root) {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setRoot(root);
        JsonFiles jsonFiles = new JsonFiles(new ObjectMapper().findAndRegisterModules());

        settingsRepository = new SettingsRepository(jsonFiles, storageProperties);
        eventService = mock(EventService.class);
        settingsService = new SettingsService(
                settingsRepository,
                new SettingsSecretsService(new InMemorySecretStore()),
                eventService
        );
    }

    @Test
    void addingLocalFolderPersistsItInApplicationSettings() {
        KnowledgeFolder folder = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest("C:\\Work"));

        assertThat(folder.getId()).isNotNull();
        assertThat(folder.getPath()).isEqualTo("C:\\Work");
        assertThat(folder.getStatus()).isEqualTo("scanned");
        assertThat(settingsRepository.get().getKnowledgeFolders())
                .extracting(KnowledgeFolder::getPath)
                .containsExactly("C:\\Work");
        verify(eventService).publish(eq("settings-updated"), any(ApplicationSettings.class));
    }

    @Test
    void removingLocalFolderPersistsTheRemoval() {
        KnowledgeFolder folder = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest("C:\\Work"));

        settingsService.removeKnowledgeFolder(folder.getId());

        assertThat(settingsRepository.get().getKnowledgeFolders()).isEmpty();
    }

    @Test
    void pauseAndResumeScanningPersistStatus() {
        KnowledgeFolder folder = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest("C:\\Work"));

        KnowledgeFolder paused = settingsService.pauseKnowledgeFolder(folder.getId());
        assertThat(paused.getStatus()).isEqualTo("paused");
        assertThat(settingsRepository.get().getKnowledgeFolders().getFirst().getStatus()).isEqualTo("paused");

        KnowledgeFolder resumed = settingsService.resumeKnowledgeFolder(folder.getId());
        assertThat(resumed.getStatus()).isEqualTo("scanned");
        assertThat(settingsRepository.get().getKnowledgeFolders().getFirst().getStatus()).isEqualTo("scanned");
    }

    @Test
    void scanNowIsANoopThatReturnsTheSavedFolder() {
        KnowledgeFolder folder = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest("C:\\Work"));
        settingsService.pauseKnowledgeFolder(folder.getId());

        KnowledgeFolder scanned = settingsService.scanKnowledgeFolder(folder.getId());

        assertThat(scanned.getId()).isEqualTo(folder.getId());
        assertThat(scanned.getStatus()).isEqualTo("paused");
        assertThat(settingsRepository.get().getKnowledgeFolders().getFirst().getStatus()).isEqualTo("paused");
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
