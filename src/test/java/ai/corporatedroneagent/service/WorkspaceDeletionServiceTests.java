package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ai.corporatedroneagent.TestDatabaseSupport;
import ai.corporatedroneagent.dto.ConversationDto;
import ai.corporatedroneagent.dto.ConversationRequest;
import ai.corporatedroneagent.dto.ProjectDeletedDto;
import ai.corporatedroneagent.job.MessagePushJob;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Project;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.repository.ProjectRepository;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkspaceDeletionServiceTests {

    private ConversationRepository conversationRepository;
    private ProjectRepository projectRepository;
    private EventService eventService;
    private MessagePushJob messagePushJob;
    private ConversationService conversationService;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = TestDatabaseSupport.migratedJdbcTemplate();

        conversationRepository = new ConversationRepository(jdbcTemplate);
        projectRepository = new ProjectRepository(jdbcTemplate);
        eventService = mock(EventService.class);
        messagePushJob = mock(MessagePushJob.class);

        conversationService = new ConversationService(
                conversationRepository, projectRepository, eventService, messagePushJob);
        projectService = new ProjectService(projectRepository, conversationRepository, eventService);
    }

    @Test
    void newConversationsAreInsertedAtTheTopOfTheProject() {
        Project project = saveProject("Q2 operations");

        ConversationDto first = conversationService.create(project.getId(), new ConversationRequest("First"));
        ConversationDto second = conversationService.create(project.getId(), new ConversationRequest("Second"));

        assertThat(projectRepository.findById(project.getId()).orElseThrow().getConversationIds())
                .containsExactly(second.id(), first.id());
    }

    @Test
    void deletingAConversationRemovesItFromStorageAndItsProject() {
        Project project = saveProject("Vendor & contracts");
        Conversation conversation = saveConversation(project, "Find the renewed NDA");

        conversationService.delete(conversation.getId());

        assertThat(conversationRepository.findById(conversation.getId())).isEmpty();
        assertThat(projectRepository.findById(project.getId()).orElseThrow().getConversationIds())
                .doesNotContain(conversation.getId());
        verify(eventService).publish(eq("conversation-deleted"), any());
    }

    @Test
    void deletingAProjectCascadesToItsConversations() {
        Project project = saveProject("My week");
        Conversation one = saveConversation(project, "Prep notes for 1:1");
        Conversation two = saveConversation(project, "Weekly review");

        projectService.delete(project.getId());

        assertThat(projectRepository.findById(project.getId())).isEmpty();
        assertThat(conversationRepository.findById(one.getId())).isEmpty();
        assertThat(conversationRepository.findById(two.getId())).isEmpty();
        verify(eventService).publish(eq("project-deleted"), eq(new ProjectDeletedDto(project.getId())));
        verify(eventService).publish(eq("projects-updated"), any());
    }

    @Test
    void sendingAUserMessagePersistsItInConversationHistory() {
        Project project = saveProject("Launch");
        Conversation conversation = saveConversation(project, "Prep");

        conversationService.sendUserMessage(conversation.getId(), "Draft the kickoff note");

        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getMessages())
                .hasSize(1)
                .first()
                .satisfies(message -> {
                    assertThat(message.getRole()).isEqualTo("user");
                    assertThat(message.getContent()).isEqualTo("Draft the kickoff note");
                    assertThat(message.getCreatedAt()).isNotNull();
                });
        verify(messagePushJob).queueAssistantReply(conversation.getId(), "Draft the kickoff note");
    }

    private Project saveProject(String name) {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName(name);
        project.setWorkingFolder("");
        project.setCustomInstructions("");
        project.setConversationIds(new ArrayList<>());
        return projectRepository.save(project);
    }

    private Conversation saveConversation(Project project, String name) {
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setProjectId(project.getId());
        conversation.setName(name);
        conversationRepository.save(conversation);

        Project stored = projectRepository.findById(project.getId()).orElseThrow();
        stored.getConversationIds().add(conversation.getId());
        projectRepository.save(stored);
        return conversation;
    }
}
