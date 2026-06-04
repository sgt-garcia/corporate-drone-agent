package ai.corporatedroneagent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.config.KnowledgeDatabaseConfig;
import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.security.SecretStore;
import ai.corporatedroneagent.service.DatabaseKeyService;
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

class KnowledgeRootRepositoryTests {

    @TempDir
    private Path storageRoot;
    private KnowledgeRootRepository repository;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setRoot(storageRoot);
        DataSource dataSource = new KnowledgeDatabaseConfig().dataSource(
                storageProperties,
                new DatabaseKeyService(new InMemorySecretStore())
        );
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        repository = new KnowledgeRootRepository(new JdbcTemplate(dataSource));
    }

    @Test
    void savesAndLoadsKnowledgeRoot() {
        KnowledgeRoot root = new KnowledgeRoot();
        root.setSource(KnowledgeSource.LOCAL_FOLDER);
        root.setReference("C:\\Data");
        root.setDisplayName("Data");
        root.setTotalResources(12);
        root.setTotalSizeBytes(3456);
        root.setScanStatus(WorkStatus.DONE);
        root.setScanSuccess(true);

        KnowledgeRoot saved = repository.save(root);

        assertThat(repository.findById(saved.getId())).hasValueSatisfying(loaded -> {
            assertThat(loaded.getSource()).isEqualTo(KnowledgeSource.LOCAL_FOLDER);
            assertThat(loaded.getReference()).isEqualTo("C:\\Data");
            assertThat(loaded.getDisplayName()).isEqualTo("Data");
            assertThat(loaded.getTotalResources()).isEqualTo(12);
            assertThat(loaded.getTotalSizeBytes()).isEqualTo(3456);
            assertThat(loaded.getScanStatus()).isEqualTo(WorkStatus.DONE);
            assertThat(loaded.getScanSuccess()).isTrue();
            assertThat(loaded.getCreatedAt()).isNotNull();
            assertThat(loaded.getUpdatedAt()).isNotNull();
        });
    }

    @Test
    void updatesKnowledgeRoot() {
        KnowledgeRoot root = new KnowledgeRoot();
        root.setSource(KnowledgeSource.LOCAL_FOLDER);
        root.setReference("C:\\Data");
        root.setDisplayName("Data");
        KnowledgeRoot saved = repository.save(root);

        saved.setDisplayName("Renamed");
        saved.setPaused(true);
        repository.save(saved);

        assertThat(repository.findById(saved.getId())).hasValueSatisfying(loaded -> {
            assertThat(loaded.getDisplayName()).isEqualTo("Renamed");
            assertThat(loaded.isPaused()).isTrue();
        });
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
