package ai.corporatedroneagent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.config.KnowledgeDatabaseConfig;
import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceIndex;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceRead;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeRootScan;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.security.SecretStore;
import ai.corporatedroneagent.service.DatabaseKeyService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeResourcePipelineRepositoryTests {

    @TempDir
    private Path storageRoot;
    private KnowledgeRootRepository rootRepository;
    private KnowledgeRootScanRepository scanRepository;
    private KnowledgeResourceRepository resourceRepository;
    private KnowledgeResourcePipelineRepository pipelineRepository;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setRoot(storageRoot);
        DataSource dataSource = new KnowledgeDatabaseConfig().dataSource(
                storageProperties,
                new DatabaseKeyService(new InMemorySecretStore())
        );
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        rootRepository = new KnowledgeRootRepository(jdbcTemplate);
        scanRepository = new KnowledgeRootScanRepository(jdbcTemplate);
        resourceRepository = new KnowledgeResourceRepository(jdbcTemplate);
        pipelineRepository = new KnowledgeResourcePipelineRepository(jdbcTemplate);
    }

    @Test
    void savesResourcePipelineState() {
        KnowledgeRoot root = root();

        KnowledgeRootScan scan = new KnowledgeRootScan();
        scan.setRootId(root.getId());
        scan.setStatus(WorkStatus.DONE);
        scan.setSuccess(true);
        scan.setTotalResources(1);
        scan.setTotalSizeBytes(5);
        KnowledgeRootScan savedScan = scanRepository.save(scan);

        assertThat(scanRepository.findLatestByRootId(root.getId())).hasValueSatisfying(loaded -> {
            assertThat(loaded.getId()).isEqualTo(savedScan.getId());
            assertThat(loaded.getStatus()).isEqualTo(WorkStatus.DONE);
            assertThat(loaded.getSuccess()).isTrue();
        });

        KnowledgeResource resource = new KnowledgeResource();
        resource.setRootId(root.getId());
        resource.setReference("docs/readme.txt");
        resource.setDisplayName("readme.txt");
        resource.setFormat("txt");
        resource.setSizeBytes(5);
        resource.setLastModifiedAt(Instant.now());
        resource.setScannedAt(Instant.now());
        KnowledgeResource savedResource = resourceRepository.save(resource);

        assertThat(resourceRepository.findByRootIdAndReference(root.getId(), "docs/readme.txt"))
                .hasValueSatisfying(loaded -> assertThat(loaded.getDisplayName()).isEqualTo("readme.txt"));

        KnowledgeResourceRead read = new KnowledgeResourceRead();
        read.setResourceId(savedResource.getId());
        read.setStatus(WorkStatus.DONE);
        read.setSuccess(true);
        read.setValue("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        read.setReadAt(Instant.now());
        pipelineRepository.saveRead(read);

        assertThat(pipelineRepository.findReadByResourceId(savedResource.getId()))
                .hasValueSatisfying(loaded -> {
                    assertThat(loaded.getReason()).isNull();
                    assertThat(loaded.getValue()).containsExactly(104, 101, 108, 108, 111);
                });

        KnowledgeResourceConversion conversion = new KnowledgeResourceConversion();
        conversion.setResourceId(savedResource.getId());
        conversion.setStatus(WorkStatus.DONE);
        conversion.setSuccess(true);
        conversion.setValue("# hello");
        conversion.setConvertedAt(Instant.now());
        pipelineRepository.saveConversion(conversion);

        assertThat(pipelineRepository.findConversionByResourceId(savedResource.getId()))
                .hasValueSatisfying(loaded -> {
                    assertThat(loaded.getReason()).isNull();
                    assertThat(loaded.getValue()).isEqualTo("# hello");
                });

        KnowledgeResourceChunk chunk = new KnowledgeResourceChunk();
        chunk.setResourceId(savedResource.getId());
        chunk.setChunkIndex(0);
        chunk.setStartOffset(0);
        chunk.setEndOffset(7);
        chunk.setContentHash("hash");
        KnowledgeResourceChunk savedChunk = pipelineRepository.insertChunk(chunk);

        assertThat(pipelineRepository.findChunksByResourceId(savedResource.getId()))
                .hasSize(1)
                .first()
                .satisfies(loaded -> {
                    assertThat(loaded.getId()).isEqualTo(savedChunk.getId());
                    assertThat(loaded.getStartOffset()).isEqualTo(0);
                    assertThat(loaded.getEndOffset()).isEqualTo(7);
                    assertThat(loaded.getContentHash()).isEqualTo("hash");
                });

        KnowledgeResourceIndex index = new KnowledgeResourceIndex();
        index.setChunkId(savedChunk.getId());
        index.setStatus(WorkStatus.DONE);
        index.setSuccess(true);
        index.setIndexReference("lucene-doc-1");
        index.setIndexedAt(Instant.now());
        pipelineRepository.saveIndex(index);

        assertThat(pipelineRepository.findIndexByChunkId(savedChunk.getId()))
                .hasValueSatisfying(loaded -> assertThat(loaded.getIndexReference()).isEqualTo("lucene-doc-1"));
    }

    @Test
    void updatesLatestReadAndConversionRows() {
        KnowledgeResource resource = resource(root());

        KnowledgeResourceRead read = new KnowledgeResourceRead();
        read.setResourceId(resource.getId());
        read.setStatus(WorkStatus.IN_PROGRESS);
        pipelineRepository.saveRead(read);

        read.setStatus(WorkStatus.DONE);
        read.setSuccess(false);
        read.setReason(KnowledgePipelineReason.READ_FAILED);
        read.setMessage("Could not read");
        pipelineRepository.saveRead(read);

        assertThat(pipelineRepository.findReadByResourceId(resource.getId())).hasValueSatisfying(loaded -> {
            assertThat(loaded.getStatus()).isEqualTo(WorkStatus.DONE);
            assertThat(loaded.getSuccess()).isFalse();
            assertThat(loaded.getReason()).isEqualTo(KnowledgePipelineReason.READ_FAILED);
            assertThat(loaded.getMessage()).isEqualTo("Could not read");
        });

        KnowledgeResourceConversion conversion = new KnowledgeResourceConversion();
        conversion.setResourceId(resource.getId());
        conversion.setValue("old");
        pipelineRepository.saveConversion(conversion);

        conversion.setValue("new");
        pipelineRepository.saveConversion(conversion);

        assertThat(pipelineRepository.findConversionByResourceId(resource.getId()))
                .hasValueSatisfying(loaded -> assertThat(loaded.getValue()).isEqualTo("new"));
    }

    @Test
    void reusableResourceLookupUsesStableReasonsInsteadOfMessages() {
        KnowledgeRoot root = root();
        KnowledgeResource unsupportedResource = resource(root, "unsupported.bin");
        KnowledgeResource undecodableResource = resource(root, "undecodable.txt");
        KnowledgeResource legacyMessageOnlyResource = resource(root, "legacy.bin");

        KnowledgeResourceRead unsupportedRead = new KnowledgeResourceRead();
        unsupportedRead.setResourceId(unsupportedResource.getId());
        unsupportedRead.setStatus(WorkStatus.DONE);
        unsupportedRead.setSuccess(false);
        unsupportedRead.setReason(KnowledgePipelineReason.UNSUPPORTED_FILE_FORMAT);
        unsupportedRead.setMessage("Localized unsupported-format text");
        pipelineRepository.saveRead(unsupportedRead);

        KnowledgeResourceConversion undecodableConversion = new KnowledgeResourceConversion();
        undecodableConversion.setResourceId(undecodableResource.getId());
        undecodableConversion.setStatus(WorkStatus.DONE);
        undecodableConversion.setSuccess(false);
        undecodableConversion.setReason(KnowledgePipelineReason.UTF8_DECODE_FAILED);
        undecodableConversion.setMessage("Could not decode resource bytes");
        pipelineRepository.saveConversion(undecodableConversion);

        KnowledgeResourceRead legacyRead = new KnowledgeResourceRead();
        legacyRead.setResourceId(legacyMessageOnlyResource.getId());
        legacyRead.setStatus(WorkStatus.DONE);
        legacyRead.setSuccess(false);
        legacyRead.setMessage("Unsupported file format");
        pipelineRepository.saveRead(legacyRead);

        assertThat(pipelineRepository.findReusablePipelineResourceIdsByRootId(root.getId()))
                .contains(unsupportedResource.getId(), undecodableResource.getId())
                .doesNotContain(legacyMessageOnlyResource.getId());
    }

    private KnowledgeRoot root() {
        KnowledgeRoot root = new KnowledgeRoot();
        root.setSource(KnowledgeSource.LOCAL_FOLDER);
        root.setReference("C:\\Data");
        root.setDisplayName("Data");
        return rootRepository.save(root);
    }

    private KnowledgeResource resource(KnowledgeRoot root) {
        return resource(root, "file.txt");
    }

    private KnowledgeResource resource(KnowledgeRoot root, String reference) {
        KnowledgeResource resource = new KnowledgeResource();
        resource.setRootId(root.getId());
        resource.setReference(reference);
        resource.setDisplayName(reference);
        return resourceRepository.save(resource);
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
