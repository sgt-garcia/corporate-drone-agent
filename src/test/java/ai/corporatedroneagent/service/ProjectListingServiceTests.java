package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.corporatedroneagent.TestDatabaseSupport;
import ai.corporatedroneagent.dto.ConversationSummaryDto;
import ai.corporatedroneagent.dto.ProjectDto;
import ai.corporatedroneagent.dto.ProjectRequest;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.model.Project;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.repository.ProjectRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ProjectListingServiceTests {

    private ProjectRepository projectRepository;
    private ConversationRepository conversationRepository;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = TestDatabaseSupport.migratedJdbcTemplate();

        projectRepository = new ProjectRepository(jdbcTemplate);
        conversationRepository = new ConversationRepository(jdbcTemplate);
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

    @Test
    void projectDtosUseConversationSummariesWithoutMessageHistory() {
        UUID projectId = saveProjectAt("Launch", Instant.parse("2026-01-01T00:00:00Z"));
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setProjectId(projectId);
        conversation.setName("Kickoff");
        conversationRepository.save(conversation);
        conversationRepository.appendMessage(
                conversation.getId(),
                new Message(UUID.randomUUID(), "user", "large history stays out of summaries", Instant.now())
        );

        Project project = projectRepository.findById(projectId).orElseThrow();
        project.getConversationIds().add(conversation.getId());
        projectRepository.save(project);

        assertThat(projectService.listProjects())
                .first()
                .satisfies(projectDto -> assertThat(projectDto.conversations())
                        .containsExactly(new ConversationSummaryDto(conversation.getId(), projectId, "Kickoff")));
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
