package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.corporatedroneagent.config.KnowledgeDatabaseConfig;
import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceRead;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeResourcePipelineRepository;
import ai.corporatedroneagent.repository.KnowledgeResourceRepository;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.repository.KnowledgeRootScanRepository;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.security.SecretStore;
import ai.corporatedroneagent.util.JsonFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private KnowledgeResourcePipelineRepository pipelineRepository;

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
        pipelineRepository = new KnowledgeResourcePipelineRepository(jdbcTemplate);
        LocalFolderKnowledgeReadService readService = new LocalFolderKnowledgeReadService(pipelineRepository);
        LocalFolderKnowledgeConversionService conversionService = new LocalFolderKnowledgeConversionService(
                pipelineRepository
        );
        KnowledgeChunkingService chunkingService = new KnowledgeChunkingService(pipelineRepository);
        LocalFolderKnowledgeScanService localScanService = new LocalFolderKnowledgeScanService(
                knowledgeRootRepository,
                knowledgeRootScanRepository,
                knowledgeResourceRepository,
                readService,
                conversionService,
                chunkingService
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
        Files.writeString(folder.resolve("one.txt"), "hello", StandardCharsets.UTF_8);
        Path nested = Files.createDirectory(folder.resolve("nested"));
        Files.write(nested.resolve("two.bin"), new byte[1024]);
        KnowledgeFolder configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));

        KnowledgeFolder scanned = scanService.scanFolder(configured.getId());

        assertThat(scanned.getStatus()).isEqualTo("scanned");
        assertThat(scanned.getFiles()).isEqualTo(2);
        assertThat(scanned.getSize()).isEqualTo("1.0 KB");
        assertThat(scanned.getNextScan()).isEqualTo("~15 min");
        assertThat(settingsRepository.get().getKnowledgeFolders().getFirst().getFiles()).isEqualTo(2);

        KnowledgeRoot knowledgeRoot = knowledgeRootRepository
                .findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, folder.toString())
                .orElseThrow();
        assertThat(knowledgeRoot.getTotalResources()).isEqualTo(2);
        assertThat(knowledgeRoot.getTotalSizeBytes()).isEqualTo(1029);
        assertThat(knowledgeRoot.getScanSuccess()).isTrue();
        assertThat(knowledgeRootScanRepository.findLatestByRootId(knowledgeRoot.getId()))
                .hasValueSatisfying(scan -> assertThat(scan.getTotalResources()).isEqualTo(2));
        assertThat(knowledgeResourceRepository.findByRootId(knowledgeRoot.getId()))
                .extracting(KnowledgeResource::getReference)
                .containsExactlyInAnyOrder("one.txt", "nested/two.bin");

        KnowledgeResource textResource = knowledgeResourceRepository
                .findByRootIdAndReference(knowledgeRoot.getId(), "one.txt")
                .orElseThrow();
        assertThat(pipelineRepository.findReadByResourceId(textResource.getId()))
                .hasValueSatisfying(read -> {
                    assertThat(read.getStatus()).isEqualTo(WorkStatus.DONE);
                    assertThat(read.getSuccess()).isTrue();
                    assertThat(new String(read.getValue(), StandardCharsets.UTF_8)).isEqualTo("hello");
                });
        assertThat(pipelineRepository.findConversionByResourceId(textResource.getId()))
                .hasValueSatisfying(conversion -> {
                    assertThat(conversion.getStatus()).isEqualTo(WorkStatus.DONE);
                    assertThat(conversion.getSuccess()).isTrue();
                    assertThat(conversion.getValue()).isEqualTo("hello");
                });
        assertThat(pipelineRepository.findChunksByResourceId(textResource.getId()))
                .hasSize(1)
                .first()
                .satisfies(chunk -> {
                    assertThat(chunk.getChunkIndex()).isZero();
                    assertThat(chunk.getStartOffset()).isZero();
                    assertThat(chunk.getEndOffset()).isEqualTo(5);
                    assertThat(chunk.getContentHash()).hasSize(64);
                });

        KnowledgeResource unsupportedResource = knowledgeResourceRepository
                .findByRootIdAndReference(knowledgeRoot.getId(), "nested/two.bin")
                .orElseThrow();
        assertThat(pipelineRepository.findReadByResourceId(unsupportedResource.getId()))
                .hasValueSatisfying(read -> {
                    assertThat(read.getStatus()).isEqualTo(WorkStatus.DONE);
                    assertThat(read.getSuccess()).isFalse();
                    assertThat(read.getMessage()).isEqualTo("Unsupported file format");
                });
        assertThat(pipelineRepository.findConversionByResourceId(unsupportedResource.getId()))
                .hasValueSatisfying(conversion -> {
                    assertThat(conversion.getStatus()).isEqualTo(WorkStatus.DONE);
                    assertThat(conversion.getSuccess()).isFalse();
                    assertThat(conversion.getMessage()).isEqualTo("Read did not succeed");
                    assertThat(conversion.getValue()).isEmpty();
                });
        assertThat(pipelineRepository.findChunksByResourceId(unsupportedResource.getId())).isEmpty();
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

    @Test
    void scanFolderUpdatesReadValueWhenTextFileChanges() throws IOException {
        Path folder = Files.createDirectory(root.resolve("read-refresh"));
        Path file = folder.resolve("notes.md");
        Files.writeString(file, "old", StandardCharsets.UTF_8);
        KnowledgeFolder configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));
        scanService.scanFolder(configured.getId());

        Files.writeString(file, "new", StandardCharsets.UTF_8);
        scanService.scanFolder(configured.getId());

        KnowledgeRoot knowledgeRoot = knowledgeRootRepository
                .findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, folder.toString())
                .orElseThrow();
        KnowledgeResource resource = knowledgeResourceRepository
                .findByRootIdAndReference(knowledgeRoot.getId(), "notes.md")
                .orElseThrow();
        Optional<KnowledgeResourceRead> read = pipelineRepository.findReadByResourceId(resource.getId());
        Optional<KnowledgeResourceConversion> conversion = pipelineRepository.findConversionByResourceId(resource.getId());
        java.util.List<KnowledgeResourceChunk> chunks = pipelineRepository.findChunksByResourceId(resource.getId());

        assertThat(read).hasValueSatisfying(value ->
                assertThat(new String(value.getValue(), StandardCharsets.UTF_8)).isEqualTo("new")
        );
        assertThat(conversion).hasValueSatisfying(value ->
                assertThat(value.getValue()).isEqualTo("new")
        );
        assertThat(chunks)
                .hasSize(1)
                .first()
                .satisfies(chunk -> assertThat(chunk.getEndOffset()).isEqualTo(3));
    }

    @Test
    void scanFolderSplitsConvertedTextIntoOverlappingChunks() throws IOException {
        Path folder = Files.createDirectory(root.resolve("chunk-me"));
        String text = "a".repeat(3500);
        Files.writeString(folder.resolve("large.txt"), text, StandardCharsets.UTF_8);
        KnowledgeFolder configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));

        scanService.scanFolder(configured.getId());

        KnowledgeRoot knowledgeRoot = knowledgeRootRepository
                .findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, folder.toString())
                .orElseThrow();
        KnowledgeResource resource = knowledgeResourceRepository
                .findByRootIdAndReference(knowledgeRoot.getId(), "large.txt")
                .orElseThrow();

        assertThat(pipelineRepository.findChunksByResourceId(resource.getId()))
                .hasSize(2)
                .satisfiesExactly(
                        chunk -> {
                            assertThat(chunk.getChunkIndex()).isZero();
                            assertThat(chunk.getStartOffset()).isZero();
                            assertThat(chunk.getEndOffset()).isEqualTo(3000);
                            assertThat(chunk.getContentHash()).hasSize(64);
                        },
                        chunk -> {
                            assertThat(chunk.getChunkIndex()).isEqualTo(1);
                            assertThat(chunk.getStartOffset()).isEqualTo(2000);
                            assertThat(chunk.getEndOffset()).isEqualTo(3500);
                            assertThat(chunk.getContentHash()).hasSize(64);
                        }
                );
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
