package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.security.SecretStore;
import ai.corporatedroneagent.util.JsonFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeFolderScanServiceTests {

    @TempDir
    private Path root;
    private SettingsRepository settingsRepository;
    private SettingsService settingsService;
    private KnowledgeFolderScanService scanService;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setRoot(root);
        JsonFiles jsonFiles = new JsonFiles(new ObjectMapper().findAndRegisterModules());
        SettingsSecretsService secretsService = new SettingsSecretsService(new InMemorySecretStore());
        EventService eventService = mock(EventService.class);

        settingsRepository = new SettingsRepository(jsonFiles, storageProperties);
        settingsService = new SettingsService(settingsRepository, secretsService, eventService);
        scanService = new KnowledgeFolderScanService(settingsRepository, secretsService, eventService);
    }

    @Test
    void scanFolderCountsFilesAndTheirSizeThenSavesMetadata() throws IOException {
        Path folder = Files.createDirectory(root.resolve("scan-me"));
        Files.write(folder.resolve("one.bin"), new byte[512]);
        Path nested = Files.createDirectory(folder.resolve("nested"));
        Files.write(nested.resolve("two.bin"), new byte[1024]);
        KnowledgeFolder configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));

        KnowledgeFolder scanned = scanService.scanFolder(configured.getId());

        assertThat(scanned.getStatus()).isEqualTo("scanned");
        assertThat(scanned.getFiles()).isEqualTo(2);
        assertThat(scanned.getSize()).isEqualTo("1.5 KB");
        assertThat(scanned.getNextScan()).isEqualTo("~15 min");
        assertThat(settingsRepository.get().getKnowledgeFolders().getFirst().getFiles()).isEqualTo(2);
    }

    @Test
    void scanAllFoldersSkipsPausedFolders() throws IOException {
        Path activeFolder = Files.createDirectory(root.resolve("active"));
        Files.write(activeFolder.resolve("active.txt"), new byte[12]);
        Path pausedFolder = Files.createDirectory(root.resolve("paused"));
        Files.write(pausedFolder.resolve("paused.txt"), new byte[12]);
        KnowledgeFolder active = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(activeFolder.toString()));
        KnowledgeFolder paused = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(pausedFolder.toString()));
        settingsService.pauseKnowledgeFolder(paused.getId());

        scanService.scanAllFolders();

        assertThat(folder(active.getId()).getFiles()).isEqualTo(1);
        assertThat(folder(active.getId()).getStatus()).isEqualTo("scanned");
        assertThat(folder(paused.getId()).getFiles()).isZero();
        assertThat(folder(paused.getId()).getStatus()).isEqualTo("paused");
    }

    private KnowledgeFolder folder(java.util.UUID id) {
        return settingsRepository.get().getKnowledgeFolders().stream()
                .filter(folder -> id.equals(folder.getId()))
                .findFirst()
                .orElseThrow();
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
