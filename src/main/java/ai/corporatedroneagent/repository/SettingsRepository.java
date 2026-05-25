package ai.corporatedroneagent.repository;

import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.util.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsRepository {

    private final JsonFiles jsonFiles;
    private final Path path;

    public SettingsRepository(JsonFiles jsonFiles, StorageProperties storageProperties) {
        this.jsonFiles = jsonFiles;
        this.path = storageProperties.getRoot().resolve("application-settings.json");
    }

    public ApplicationSettings get() {
        if (!Files.exists(path)) {
            return save(new ApplicationSettings());
        }

        return jsonFiles.read(path, ApplicationSettings.class);
    }

    public ApplicationSettings save(ApplicationSettings settings) {
        jsonFiles.write(path, settings);
        return settings;
    }
}
