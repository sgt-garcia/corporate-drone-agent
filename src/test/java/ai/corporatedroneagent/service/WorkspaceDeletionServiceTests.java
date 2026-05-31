package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.dto.ConversationDto;
import ai.corporatedroneagent.dto.ConversationRequest;
import ai.corporatedroneagent.job.MessagePushJob;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Project;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.repository.ProjectRepository;
import ai.corporatedroneagent.util.JsonFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceDeletionServiceTests {

    private ConversationRepository conversationRepository;
    private ProjectRepository projectRepository;
    private EventService eventService;
    private MessagePushJob messagePushJob;
    private ConversationService conversationService;
    private ProjectService projectService;

    @BeforeEach
    void setUp(@TempDir Path root) {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setRoot(root);
        JsonFiles jsonFiles = new JsonFiles(new ObjectMapper().findAndRegisterModules());

        conversationRepository = new ConversationRepository(jsonFiles, storageProperties);
        projectRepository = new ProjectRepository(jsonFiles, storageProperties);
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
        verify(eventService).publish(eq("project-deleted"), eq(project.getId().toString()));
        verify(eventService).publish(eq("projects-updated"), any());
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
