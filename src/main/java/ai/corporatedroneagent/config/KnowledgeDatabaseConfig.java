package ai.corporatedroneagent.config;

import ai.corporatedroneagent.service.DatabaseKeyService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeDatabaseConfig {

    private static final String H2_USER = "sa";
    private static final String H2_USER_PASSWORD = "cda";

    @Bean
    public DataSource dataSource(StorageProperties storageProperties, DatabaseKeyService databaseKeyService) {
        Path databaseDirectory = storageProperties.getRoot().resolve("database");
        try {
            Files.createDirectories(databaseDirectory);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create knowledge database directory " + databaseDirectory, exception);
        }

        Path databasePath = databaseDirectory.resolve("knowledge");
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("knowledge-database-pool");
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:file:" + databasePath.toAbsolutePath() + ";CIPHER=AES;DB_CLOSE_DELAY=-1");
        dataSource.setUsername(H2_USER);
        dataSource.setPassword(databaseKeyService.encryptionKey() + " " + H2_USER_PASSWORD);
        return dataSource;
    }
}
