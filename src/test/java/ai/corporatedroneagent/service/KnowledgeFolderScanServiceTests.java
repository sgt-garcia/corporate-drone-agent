package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ai.corporatedroneagent.config.KnowledgeDatabaseConfig;
import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.dto.KnowledgeFolderDto;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceIndex;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

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
    private KnowledgeIndexingService indexingService;
    private KnowledgeSearchService searchService;
    private KnowledgeScanCoordinator scanCoordinator;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setRoot(root);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        InMemorySecretStore secretStore = new InMemorySecretStore();
        SettingsSecretsService secretsService = new SettingsSecretsService(secretStore);
        EventService eventService = mock(EventService.class);
        DataSource dataSource = new KnowledgeDatabaseConfig().dataSource(
                storageProperties,
                new DatabaseKeyService(secretStore)
        );
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        knowledgeRootRepository = new KnowledgeRootRepository(jdbcTemplate);
        knowledgeRootScanRepository = new KnowledgeRootScanRepository(jdbcTemplate);
        knowledgeResourceRepository = new KnowledgeResourceRepository(jdbcTemplate);
        pipelineRepository = new KnowledgeResourcePipelineRepository(jdbcTemplate);
        LocalFolderKnowledgeReadService readService = new LocalFolderKnowledgeReadService(pipelineRepository);
        LocalFolderKnowledgeConversionService conversionService = new LocalFolderKnowledgeConversionService(
                pipelineRepository
        );
        KnowledgeChunkingService chunkingService = new KnowledgeChunkingService(pipelineRepository);
        indexingService = new KnowledgeIndexingService(pipelineRepository, storageProperties);
        scanCoordinator = new KnowledgeScanCoordinator();
        searchService = new KnowledgeSearchService(
                indexingService,
                pipelineRepository,
                knowledgeResourceRepository,
                knowledgeRootRepository
        );
        settingsRepository = new SettingsRepository(jdbcTemplate, objectMapper);
        settingsService = new SettingsService(
                settingsRepository,
                knowledgeRootRepository,
                secretsService,
                eventService,
                new KnowledgeRootCleanupService(
                        knowledgeRootRepository,
                        knowledgeResourceRepository,
                        indexingService
                ),
                scanCoordinator
        );
        LocalFolderKnowledgeScanService localScanService = new LocalFolderKnowledgeScanService(
                knowledgeRootRepository,
                knowledgeRootScanRepository,
                knowledgeResourceRepository,
                pipelineRepository,
                readService,
                conversionService,
                chunkingService,
                indexingService
        );
        scanService = new KnowledgeFolderScanService(
                settingsService,
                knowledgeRootRepository,
                eventService,
                localScanService,
                scanCoordinator
        );
    }

    @Test
    void scanFolderCountsFilesAndTheirSizeThenSavesMetadata() throws IOException {
        Path folder = Files.createDirectory(root.resolve("scan-me"));
        Files.writeString(folder.resolve("one.txt"), "hello", StandardCharsets.UTF_8);
        Path nested = Files.createDirectory(folder.resolve("nested"));
        Files.write(nested.resolve("two.bin"), new byte[1024]);
        KnowledgeFolderDto configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));

        KnowledgeFolderDto scanned = scanService.scanFolder(configured.getId());

        assertThat(scanned.getStatus()).isEqualTo("scanned");
        assertThat(scanned.getFiles()).isEqualTo(2);
        assertThat(scanned.getSize()).isEqualTo("1.0 KB");
        assertThat(scanned.getNextScan()).isEqualTo("~15 min");
        assertThat(settingsService.listKnowledgeFolders().getFirst().getFiles()).isEqualTo(2);

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
        java.util.List<KnowledgeResourceChunk> textChunks = pipelineRepository.findChunksByResourceId(textResource.getId());
        assertThat(textChunks)
                .hasSize(1)
                .first()
                .satisfies(chunk -> {
                    assertThat(chunk.getChunkIndex()).isZero();
                    assertThat(chunk.getStartOffset()).isZero();
                    assertThat(chunk.getEndOffset()).isEqualTo(5);
                    assertThat(chunk.getContentHash()).hasSize(64);
                });
        KnowledgeResourceChunk textChunk = textChunks.getFirst();
        assertThat(pipelineRepository.findIndexByChunkId(textChunk.getId()))
                .hasValueSatisfying(index -> {
                    assertThat(index.getStatus()).isEqualTo(WorkStatus.DONE);
                    assertThat(index.getSuccess()).isTrue();
                    assertThat(index.getIndexReference()).isEqualTo(textResource.getId() + ":0");
                });
        assertThat(indexingService.searchTerm("hello", 10))
                .containsExactly(textResource.getId() + ":0");
        assertThat(searchService.search("hello", 10))
                .hasSize(1)
                .first()
                .satisfies(snippet -> {
                    assertThat(snippet.rootName()).isEqualTo("scan-me");
                    assertThat(snippet.resourceReference()).isEqualTo("one.txt");
                    assertThat(snippet.content()).isEqualTo("hello");
                });

        KnowledgeResource unsupportedResource = knowledgeResourceRepository
                .findByRootIdAndReference(knowledgeRoot.getId(), "nested/two.bin")
                .orElseThrow();
        assertThat(pipelineRepository.findReadByResourceId(unsupportedResource.getId()))
                .hasValueSatisfying(read -> {
                    assertThat(read.getStatus()).isEqualTo(WorkStatus.DONE);
                    assertThat(read.getSuccess()).isFalse();
                    assertThat(read.getReason()).isEqualTo(KnowledgePipelineReason.UNSUPPORTED_FILE_FORMAT);
                    assertThat(read.getMessage()).isEqualTo("Unsupported file format");
                });
        assertThat(pipelineRepository.findConversionByResourceId(unsupportedResource.getId()))
                .hasValueSatisfying(conversion -> {
                    assertThat(conversion.getStatus()).isEqualTo(WorkStatus.DONE);
                    assertThat(conversion.getSuccess()).isFalse();
                    assertThat(conversion.getReason()).isEqualTo(KnowledgePipelineReason.READ_DID_NOT_SUCCEED);
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
        KnowledgeFolderDto active = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(activeFolder.toString()));
        KnowledgeFolderDto paused = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(pausedFolder.toString()));
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
        Path staleFile = Files.writeString(folder.resolve("stale.txt"), "stale", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("kept.txt"), "kept", StandardCharsets.UTF_8);
        KnowledgeFolderDto configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));
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
        KnowledgeResource staleResource = knowledgeResourceRepository
                .findByRootIdAndReference(knowledgeRoot.getId(), "stale.txt")
                .orElseThrow();
        assertThat(pipelineRepository.findChunksByResourceId(staleResource.getId())).isEmpty();
        assertThat(indexingService.searchTerm("stale", 10)).isEmpty();
    }

    @Test
    void removingFolderDeletesKnowledgeRootAndIndexedContent() throws IOException {
        Path folder = Files.createDirectory(root.resolve("remove-me"));
        Files.writeString(folder.resolve("removed.txt"), "remove-token", StandardCharsets.UTF_8);
        KnowledgeFolderDto configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));
        scanService.scanFolder(configured.getId());

        assertThat(searchService.search("remove-token", 10)).hasSize(1);

        settingsService.removeKnowledgeFolder(configured.getId());

        assertThat(knowledgeRootRepository.findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, folder.toString()))
                .isEmpty();
        assertThat(indexingService.searchTerm("remove-token", 10)).isEmpty();
        assertThat(searchService.search("remove-token", 10)).isEmpty();
    }

    @Test
    void removingFolderStopsRunningScanBeforeNextFile() throws Exception {
        Path folder = Files.createDirectory(root.resolve("remove-while-scanning"));
        Files.writeString(folder.resolve("first.txt"), "first-token", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("second.txt"), "second-token", StandardCharsets.UTF_8);
        KnowledgeFolderDto configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));
        CountDownLatch firstReadStarted = new CountDownLatch(1);
        CountDownLatch allowFirstReadToFinish = new CountDownLatch(1);
        AtomicBoolean blockedFirstRead = new AtomicBoolean();
        AtomicInteger reads = new AtomicInteger();
        LocalFolderKnowledgeReadService blockingReadService = new LocalFolderKnowledgeReadService(pipelineRepository) {
            @Override
            public KnowledgeResourceRead read(KnowledgeResource resource, Path file) {
                reads.incrementAndGet();
                if (blockedFirstRead.compareAndSet(false, true)) {
                    firstReadStarted.countDown();
                    try {
                        assertThat(allowFirstReadToFinish.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }
                return super.read(resource, file);
            }
        };
        LocalFolderKnowledgeScanService blockingLocalScanService = new LocalFolderKnowledgeScanService(
                knowledgeRootRepository,
                knowledgeRootScanRepository,
                knowledgeResourceRepository,
                pipelineRepository,
                blockingReadService,
                new LocalFolderKnowledgeConversionService(pipelineRepository),
                new KnowledgeChunkingService(pipelineRepository),
                indexingService
        );
        KnowledgeFolderScanService blockingScanService = new KnowledgeFolderScanService(
                settingsService,
                knowledgeRootRepository,
                mock(EventService.class),
                blockingLocalScanService,
                scanCoordinator
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> scan = executor.submit(() -> blockingScanService.scanFolder(configured.getId()));
            assertThat(firstReadStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> removal = executor.submit(() -> settingsService.removeKnowledgeFolder(configured.getId()));

            allowFirstReadToFinish.countDown();

            assertThatThrownBy(() -> scan.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ResponseStatusException.class);
            removal.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(reads.get()).isEqualTo(1);
        assertThat(settingsService.listKnowledgeFolders()).isEmpty();
        assertThat(knowledgeRootRepository.findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, folder.toString()))
                .isEmpty();
        assertThat(searchService.search("first-token", 10)).isEmpty();
        assertThat(searchService.search("second-token", 10)).isEmpty();
    }

    @Test
    void scanFolderRecordsFailureWhenPipelineThrows() throws IOException {
        Path folder = Files.createDirectory(root.resolve("fail-me"));
        Files.writeString(folder.resolve("broken.txt"), "broken", StandardCharsets.UTF_8);
        KnowledgeFolderDto configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));
        LocalFolderKnowledgeReadService failingReadService = new LocalFolderKnowledgeReadService(pipelineRepository) {
            @Override
            public KnowledgeResourceRead read(KnowledgeResource resource, Path file) {
                throw new IllegalStateException("boom");
            }
        };
        LocalFolderKnowledgeScanService failingLocalScanService = new LocalFolderKnowledgeScanService(
                knowledgeRootRepository,
                knowledgeRootScanRepository,
                knowledgeResourceRepository,
                pipelineRepository,
                failingReadService,
                new LocalFolderKnowledgeConversionService(pipelineRepository),
                new KnowledgeChunkingService(pipelineRepository),
                indexingService
        );
        KnowledgeFolderScanService failingScanService = new KnowledgeFolderScanService(
                settingsService,
                knowledgeRootRepository,
                mock(EventService.class),
                failingLocalScanService,
                new KnowledgeScanCoordinator()
        );

        assertThatThrownBy(() -> failingScanService.scanFolder(configured.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Could not scan folder");

        assertThat(folder(configured.getId()).getStatus()).isEqualTo("scanned");
        KnowledgeRoot knowledgeRoot = knowledgeRootRepository
                .findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, folder.toString())
                .orElseThrow();
        assertThat(knowledgeRoot.getScanStatus()).isEqualTo(WorkStatus.DONE);
        assertThat(knowledgeRoot.getScanSuccess()).isFalse();
        assertThat(knowledgeRoot.getScanMessage()).isEqualTo("Could not scan folder");
        assertThat(knowledgeRootScanRepository.findLatestByRootId(knowledgeRoot.getId()))
                .hasValueSatisfying(scan -> {
                    assertThat(scan.getStatus()).isEqualTo(WorkStatus.DONE);
                    assertThat(scan.getSuccess()).isFalse();
                });
    }

    @Test
    void scanFolderSkipsTextFilesLargerThanOneMb() throws IOException {
        Path folder = Files.createDirectory(root.resolve("too-big"));
        byte[] content = new byte[(int) LocalFolderKnowledgeReadService.MAX_READ_BYTES + 1];
        java.util.Arrays.fill(content, (byte) 'x');
        Files.write(folder.resolve("large.txt"), content);
        KnowledgeFolderDto configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));

        KnowledgeFolderDto scanned = scanService.scanFolder(configured.getId());

        assertThat(scanned.getFiles()).isEqualTo(1);
        assertThat(scanned.getSize()).isEqualTo("1.0 MB");
        KnowledgeRoot knowledgeRoot = knowledgeRootRepository
                .findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, folder.toString())
                .orElseThrow();
        KnowledgeResource resource = knowledgeResourceRepository
                .findByRootIdAndReference(knowledgeRoot.getId(), "large.txt")
                .orElseThrow();
        assertThat(resource.getSizeBytes()).isEqualTo(LocalFolderKnowledgeReadService.MAX_READ_BYTES + 1);
        assertThat(pipelineRepository.findReadByResourceId(resource.getId()))
                .hasValueSatisfying(read -> {
                    assertThat(read.getStatus()).isEqualTo(WorkStatus.DONE);
                    assertThat(read.getSuccess()).isFalse();
                    assertThat(read.getReason()).isEqualTo(KnowledgePipelineReason.FILE_TOO_LARGE);
                    assertThat(read.getMessage()).isEqualTo("File is larger than 1 MB");
                    assertThat(read.getValue()).isNull();
                });
        assertThat(pipelineRepository.findConversionByResourceId(resource.getId()))
                .hasValueSatisfying(conversion -> {
                    assertThat(conversion.getStatus()).isEqualTo(WorkStatus.DONE);
                    assertThat(conversion.getSuccess()).isFalse();
                    assertThat(conversion.getReason()).isEqualTo(KnowledgePipelineReason.READ_DID_NOT_SUCCEED);
                    assertThat(conversion.getMessage()).isEqualTo("Read did not succeed");
                });
        assertThat(pipelineRepository.findChunksByResourceId(resource.getId())).isEmpty();
        assertThat(searchService.search("xxx", 10)).isEmpty();
    }

    @Test
    void scanFolderUpdatesReadValueWhenTextFileChanges() throws IOException {
        Path folder = Files.createDirectory(root.resolve("read-refresh"));
        Path file = folder.resolve("notes.md");
        Files.writeString(file, "old", StandardCharsets.UTF_8);
        KnowledgeFolderDto configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));
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
                .satisfies(chunk -> {
                    assertThat(chunk.getEndOffset()).isEqualTo(3);
                    assertThat(pipelineRepository.findIndexByChunkId(chunk.getId()))
                            .map(KnowledgeResourceIndex::getSuccess)
                            .contains(true);
                });
        assertThat(indexingService.searchTerm("old", 10)).isEmpty();
        assertThat(indexingService.searchTerm("new", 10)).containsExactly(resource.getId() + ":0");
        assertThat(searchService.search("new", 10))
                .hasSize(1)
                .first()
                .satisfies(snippet -> assertThat(snippet.content()).isEqualTo("new"));
    }

    @Test
    void scanFolderDoesNotReprocessUnchangedFiles() throws IOException {
        Path folder = Files.createDirectory(root.resolve("skip-unchanged"));
        Path file = folder.resolve("notes.md");
        Files.writeString(file, "oldtoken", StandardCharsets.UTF_8);
        KnowledgeFolderDto configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));
        AtomicInteger reads = new AtomicInteger();
        LocalFolderKnowledgeReadService countingReadService = new LocalFolderKnowledgeReadService(pipelineRepository) {
            @Override
            public KnowledgeResourceRead read(KnowledgeResource resource, Path file) {
                reads.incrementAndGet();
                return super.read(resource, file);
            }
        };
        LocalFolderKnowledgeScanService countingLocalScanService = new LocalFolderKnowledgeScanService(
                knowledgeRootRepository,
                knowledgeRootScanRepository,
                knowledgeResourceRepository,
                pipelineRepository,
                countingReadService,
                new LocalFolderKnowledgeConversionService(pipelineRepository),
                new KnowledgeChunkingService(pipelineRepository),
                indexingService
        );
        KnowledgeFolderScanService countingScanService = new KnowledgeFolderScanService(
                settingsService,
                knowledgeRootRepository,
                mock(EventService.class),
                countingLocalScanService,
                scanCoordinator
        );

        countingScanService.scanFolder(configured.getId());
        countingScanService.scanFolder(configured.getId());

        assertThat(reads.get()).isEqualTo(1);
        assertThat(searchService.search("oldtoken", 10)).hasSize(1);

        Files.writeString(file, "newtoken", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(5)));
        countingScanService.scanFolder(configured.getId());

        KnowledgeRoot knowledgeRoot = knowledgeRootRepository
                .findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, folder.toString())
                .orElseThrow();
        KnowledgeResource resource = knowledgeResourceRepository
                .findByRootIdAndReference(knowledgeRoot.getId(), "notes.md")
                .orElseThrow();
        assertThat(reads.get()).isEqualTo(2);
        assertThat(indexingService.searchTerm("oldtoken", 10)).isEmpty();
        assertThat(indexingService.searchTerm("newtoken", 10)).containsExactly(resource.getId() + ":0");
    }

    @Test
    void scanFolderSplitsConvertedTextIntoOverlappingChunks() throws IOException {
        Path folder = Files.createDirectory(root.resolve("chunk-me"));
        String text = "a".repeat(3500);
        Files.writeString(folder.resolve("large.txt"), text, StandardCharsets.UTF_8);
        KnowledgeFolderDto configured = settingsService.addKnowledgeFolder(new KnowledgeFolderRequest(folder.toString()));

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

    private KnowledgeFolderDto folder(java.util.UUID id) {
        return settingsService.listKnowledgeFolders().stream()
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
