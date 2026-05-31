package ai.corporatedroneagent.repository;

import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.model.Project;
import ai.corporatedroneagent.util.JsonFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {

    private final JsonFiles jsonFiles;
    private final Path directory;

    public ProjectRepository(JsonFiles jsonFiles, StorageProperties storageProperties) {
        this.jsonFiles = jsonFiles;
        this.directory = storageProperties.getRoot().resolve("projects");
    }

    public List<Project> findAll() {
        try {
            if (!Files.exists(directory)) {
                return List.of();
            }

            try (var stream = Files.list(directory)) {
                return stream
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .map(path -> jsonFiles.read(path, Project.class))
                        .toList();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not list projects", exception);
        }
    }

    public Optional<Project> findById(UUID id) {
        Path path = filePath(id);
        return Files.exists(path) ? Optional.of(jsonFiles.read(path, Project.class)) : Optional.empty();
    }

    public Project save(Project project) {
        jsonFiles.write(filePath(project.getId()), project);
        return project;
    }

    public synchronized boolean delete(UUID id) {
        try {
            return Files.deleteIfExists(filePath(id));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not delete project " + id, exception);
        }
    }

    private Path filePath(UUID id) {
        return directory.resolve(id + ".json");
    }
}
