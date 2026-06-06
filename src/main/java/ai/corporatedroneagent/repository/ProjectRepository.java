package ai.corporatedroneagent.repository;

import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.model.Project;
import ai.corporatedroneagent.util.JsonFiles;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {

    private final JsonFiles.DocumentStore<Project> projects;

    public ProjectRepository(JsonFiles jsonFiles, StorageProperties storageProperties) {
        this.projects = jsonFiles.documentStore(
                storageProperties.getRoot().resolve("projects"),
                Project.class,
                Project::getId,
                "project",
                "projects");
    }

    public List<Project> findAll() {
        return projects.findAll();
    }

    public Optional<Project> findById(UUID id) {
        return projects.findById(id);
    }

    public Project save(Project project) {
        return projects.save(project);
    }

    public synchronized boolean delete(UUID id) {
        return projects.delete(id);
    }
}
