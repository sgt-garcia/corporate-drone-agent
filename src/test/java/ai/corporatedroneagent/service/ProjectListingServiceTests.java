package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.dto.ProjectDto;
import ai.corporatedroneagent.dto.ProjectRequest;
import ai.corporatedroneagent.model.Project;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.repository.ProjectRepository;
import ai.corporatedroneagent.util.JsonFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectListingServiceTests {

    private ProjectRepository projectRepository;
    private ProjectService projectService;

    @BeforeEach
    void setUp(@TempDir Path root) {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setRoot(root);
        JsonFiles jsonFiles = new JsonFiles(new ObjectMapper().findAndRegisterModules());

        projectRepository = new ProjectRepository(jsonFiles, storageProperties);
        ConversationRepository conversationRepository =
                new ConversationRepository(jsonFiles, storageProperties);
        projectService = new ProjectService(
                projectRepository, conversationRepository, mock(EventService.class));
    }

    @Test
    void createStampsTheProjectWithACreationTimestamp() {
        ProjectDto created = projectService.create(new ProjectRequest("Q2 operations", "", ""));

        assertThat(created.createdAt()).isNotNull();
        assertThat(projectRepository.findById(created.id()).orElseThrow().getCreatedAt())
                .isEqualTo(created.createdAt());
    }

    @Test
    void listProjectsReturnsNewestFirst() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        UUID oldest = saveProjectAt("Bravo", base);
        UUID middle = saveProjectAt("Alpha", base.plusSeconds(60));
        UUID newest = saveProjectAt("Charlie", base.plusSeconds(120));

        assertThat(projectService.listProjects())
                .extracting(ProjectDto::id)
                .containsExactly(newest, middle, oldest);
    }

    @Test
    void projectsWithoutATimestampSortLastThenAlphabetically() {
        UUID timestamped = saveProjectAt("Zeta", Instant.parse("2026-01-01T00:00:00Z"));
        UUID legacyB = saveProjectAt("Bravo", null);
        UUID legacyA = saveProjectAt("Alpha", null);

        assertThat(projectService.listProjects())
                .extracting(ProjectDto::id)
                .containsExactly(timestamped, legacyA, legacyB);
    }

    private UUID saveProjectAt(String name, Instant createdAt) {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName(name);
        project.setWorkingFolder("");
        project.setCustomInstructions("");
        project.setCreatedAt(createdAt);
        project.setConversationIds(new ArrayList<>());
        projectRepository.save(project);
        return project.getId();
    }
}
