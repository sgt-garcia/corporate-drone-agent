package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.corporatedroneagent.config.KnowledgeDatabaseConfig;
import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.repository.KnowledgeResourceRepository;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.repository.KnowledgeRootScanRepository;
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
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeFolderScanServiceTests {

    @TempDir
    private Path root;
    private SettingsRepository settingsRepository;
    private SettingsService settingsService;
    private KnowledgeFolderScanService scanService;
    private KnowledgeRootRepository knowledgeRootRepository;
    private KnowledgeRootScanRepository knowledgeRootScanRepository;
    private KnowledgeResourceRepository knowledgeResourceRepository;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setRoot(root);
        JsonFiles jsonFiles = new JsonFiles(new ObjectMapper().findAndRegisterModules());
        InMemorySecretStore secretStore = new InMemorySecretStore();
        SettingsSecretsService secretsService = new SettingsSecretsService(secretStore);
        EventService eventService = mock(EventService.class);
        DataSource dataSource = new KnowledgeDatabaseConfig().dataSource(
                storageProperties,
                new DatabaseKeyService(secretStore)
        );
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        settingsRepository = new SettingsRepository(jsonFiles, storageProperties);
        settingsService = new SettingsService(settingsRepository, secretsService, eventService);
        knowledgeRootRepository = new KnowledgeRootRepository(jdbcTemplate);
        knowledgeRootScanRepository = new KnowledgeRootScanRepository(jdbcTemplate);
        knowledgeResourceRepository = new KnowledgeResourceRepository(jdbcTemplate);
        LocalFolderKnowledgeScanService localScanService = new LocalFolderKnowledgeScanService(
                knowledgeRootRepository,
                knowledgeRootScanRepository,
                knowledgeResourceRepository
        );
        scanService = new KnowledgeFolderScanService(
                settingsRepository,
                secretsService,
                eventService,
                localScanService
        );
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

        KnowledgeRoot knowledgeRoot = knowledgeRootRepository
                .findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, folder.toString())
                .orElseThrow();
        assertThat(knowledgeRoot.getTotalResources()).isEqualTo(2);
        assertThat(knowledgeRoot.getTotalSizeBytes()).isEqualTo(1536);
        assertThat(knowledgeRoot.getScanSuccess()).isTrue();
        assertThat(knowledgeRootScanRepository.findLatestByRootId(knowledgeRoot.getId()))
                .hasValueSatisfying(scan -> assertThat(scan.getTotalResources()).isEqualTo(2));
        assertThat(knowledgeResourceRepository.findByRootId(knowledgeRoot.getId()))
                .extracting(KnowledgeResource::getReference)
                .containsExactlyInAnyOrder("one.bin", "nested/two.bin");
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

    @Test
    void scanFolderMarksResourcesDeletedWhenTheyDisappear() throws IOException {
        Path folder = Files.createDirectory(root.resolve("refresh-me"));
        Path staleFile = Files.write(folder.resolve("stale.txt"), new byte[3]);
        Files.write(folder.resolve("kept.txt"), new byte[4]);
        KnowledgeFolder configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));
        scanService.scanFolder(configured.getId());

        Files.delete(staleFile);
        scanService.scanFolder(configured.getId());

        KnowledgeRoot knowledgeRoot = knowledgeRootRepository
                .findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, folder.toString())
                .orElseThrow();
        assertThat(knowledgeResourceRepository.findByRootIdAndReference(knowledgeRoot.getId(), "stale.txt"))
                .hasValueSatisfying(resource -> assertThat(resource.isDeleted()).isTrue());
        assertThat(knowledgeResourceRepository.findByRootIdAndReference(knowledgeRoot.getId(), "kept.txt"))
                .hasValueSatisfying(resource -> assertThat(resource.isDeleted()).isFalse());
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
