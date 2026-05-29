package ai.corporatedroneagent.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cda.storage")
public class StorageProperties {

    private static final String DEFAULT_DIRECTORY_NAME = ".corporate-drone-agent";

    private Path root = defaultRoot();

    public Path getRoot() {
        return root;
    }

    public void setRoot(Path root) {
        this.root = root;
    }

    public static Path defaultRoot() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return Path.of(DEFAULT_DIRECTORY_NAME);
        }
        return Path.of(userHome, DEFAULT_DIRECTORY_NAME);
    }
}
