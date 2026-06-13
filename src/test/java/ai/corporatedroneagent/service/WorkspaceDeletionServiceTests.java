package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ai.corporatedroneagent.TestDatabaseSupport;
import ai.corporatedroneagent.dto.ConversationDto;
import ai.corporatedroneagent.dto.ConversationRequest;
import ai.corporatedroneagent.dto.ProjectDeletedDto;
import ai.corporatedroneagent.job.MessagePushJob;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Project;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.repository.ProjectRepository;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

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
        verify(eventService).publish(eq("projects-updated"));
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
        verify(messagePushJob)
                .queueAssistantReply(eq(conversation.getId()), any(UUID.class), eq("Draft the kickoff note"));
    }

    @Test
    void appendedMessagesKeepTheirInsertOrder() {
        Project project = saveProject("Launch");
        Conversation conversation = saveConversation(project, "Prep");

        conversationService.sendUserMessage(conversation.getId(), "First");
        conversationService.sendUserMessage(conversation.getId(), "Second");

        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getMessages())
                .extracting(message -> message.getContent())
                .containsExactly("First", "Second");
    }

    @Test
    void retryingRegeneratesTheReplyForTheLastUserMessageWithoutAddingATurn() {
        Project project = saveProject("Launch");
        Conversation conversation = saveConversation(project, "Prep");
        conversationService.sendUserMessage(conversation.getId(), "Draft the kickoff note");

        conversationService.retryLastReply(conversation.getId());

        // No duplicate prompt is appended — only the original user turn remains.
        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getMessages())
                .extracting(Message::getContent)
                .containsExactly("Draft the kickoff note");
        // The reply is re-queued for that same message: once on send, once on retry.
        verify(messagePushJob, times(2))
                .queueAssistantReply(eq(conversation.getId()), any(UUID.class), eq("Draft the kickoff note"));
    }

    @Test
    void retryingAConversationWithNoUserMessageIsRejected() {
        Project project = saveProject("Launch");
        Conversation conversation = saveConversation(project, "Prep");

        assertThatThrownBy(() -> conversationService.retryLastReply(conversation.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void regeneratingHandsThePersistedAssistantReplyToThePushJobToReplace() {
        Project project = saveProject("Launch");
        Conversation conversation = saveConversation(project, "Prep");
        conversationService.sendUserMessage(conversation.getId(), "Draft the kickoff note");
        // Stand in for the reply the (mocked) push job would have appended.
        UUID replyId = UUID.randomUUID();
        conversationRepository.appendMessage(
                conversation.getId(),
                new Message(replyId, "assistant", "Here is a draft", null));

        conversationService.regenerateLastReply(conversation.getId());

        // The existing reply is NOT dropped up front. Its id is handed to the push
        // job, which deletes it only once the replacement has been persisted — so a
        // failed regenerate cannot wipe the original. Nothing is removed here (the
        // push job is mocked) and no message-deleted is published by the service.
        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getMessages())
                .extracting(Message::getContent)
                .containsExactly("Draft the kickoff note", "Here is a draft");
        verify(eventService, never()).publish(eq("message-deleted"), any());
        // Re-queued for the same user message, carrying the reply to replace.
        verify(messagePushJob).queueAssistantReply(
                eq(conversation.getId()), any(UUID.class), eq("Draft the kickoff note"), eq(replyId));
    }

    @Test
    void regeneratingWithNoPersistedReplyRequeuesWithNothingToReplace() {
        // The previous reply errored, so it was never persisted — the last turn is
        // still the user message. Regenerate has no reply to replace, so it re-queues
        // with a null replacement id.
        Project project = saveProject("Launch");
        Conversation conversation = saveConversation(project, "Prep");
        conversationService.sendUserMessage(conversation.getId(), "Draft the kickoff note");

        conversationService.regenerateLastReply(conversation.getId());

        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getMessages())
                .extracting(Message::getContent)
                .containsExactly("Draft the kickoff note");
        verify(eventService, never()).publish(eq("message-deleted"), any());
        verify(messagePushJob).queueAssistantReply(
                eq(conversation.getId()), any(UUID.class), eq("Draft the kickoff note"), isNull());
    }

    @Test
    void regeneratingWhileAReplyIsAlreadyInFlightIsIgnored() {
        // A reply is mid-flight (status "running"). A second regenerate — e.g. a
        // double-clicked button — must not stack a duplicate reply onto the turn.
        Project project = saveProject("Launch");
        Conversation conversation = saveConversation(project, "Prep");
        seedUserMessage(conversation, "Draft the kickoff note");
        conversationRepository.updateStatus(conversation.getId(), "running");

        conversationService.regenerateLastReply(conversation.getId());

        verify(messagePushJob, never()).queueAssistantReply(
                any(UUID.class), any(UUID.class), any(String.class), any());
    }

    @Test
    void retryingWhileAReplyIsAlreadyInFlightIsIgnored() {
        Project project = saveProject("Launch");
        Conversation conversation = saveConversation(project, "Prep");
        seedUserMessage(conversation, "Draft the kickoff note");
        conversationRepository.updateStatus(conversation.getId(), "running");

        conversationService.retryLastReply(conversation.getId());

        verify(messagePushJob, never()).queueAssistantReply(
                any(UUID.class), any(UUID.class), any(String.class));
    }

    @Test
    void markingAReviewConversationSeenMovesItToSuccess() {
        Project project = saveProject("Launch");
        Conversation conversation = saveConversation(project, "Prep");
        conversationRepository.updateStatus(conversation.getId(), "review");

        conversationService.markSeen(conversation.getId());

        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getStatus())
                .isEqualTo("success");
        verify(eventService).publish(eq("conversation-status"), any());
    }

    @Test
    void markingANonReviewConversationSeenLeavesItsStatusUnchanged() {
        Project project = saveProject("Launch");
        Conversation conversation = saveConversation(project, "Prep");
        conversationRepository.updateStatus(conversation.getId(), "running");

        conversationService.markSeen(conversation.getId());

        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getStatus())
                .isEqualTo("running");
        verify(eventService, never()).publish(eq("conversation-status"), any());
    }

    @Test
    void regeneratingAConversationWithNoUserMessageIsRejected() {
        Project project = saveProject("Launch");
        Conversation conversation = saveConversation(project, "Prep");

        assertThatThrownBy(() -> conversationService.regenerateLastReply(conversation.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void savingAProjectDoesNotReparentForeignConversations() {
        Project firstProject = saveProject("First");
        Project secondProject = saveProject("Second");
        Conversation secondConversation = saveConversation(secondProject, "Second conversation");

        Project storedFirstProject = projectRepository.findById(firstProject.getId()).orElseThrow();
        storedFirstProject.getConversationIds().add(secondConversation.getId());
        projectRepository.save(storedFirstProject);

        assertThat(conversationRepository.findById(secondConversation.getId()).orElseThrow().getProjectId())
                .isEqualTo(secondProject.getId());
        assertThat(projectRepository.findById(firstProject.getId()).orElseThrow().getConversationIds())
                .doesNotContain(secondConversation.getId());
        assertThat(projectRepository.findById(secondProject.getId()).orElseThrow().getConversationIds())
                .containsExactly(secondConversation.getId());
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

    // Append a user turn straight through the repository, bypassing sendUserMessage
    // so the (mocked) push job records no queue call the test would have to account
    // for.
    private void seedUserMessage(Conversation conversation, String content) {
        conversationRepository.appendMessage(
                conversation.getId(),
                new Message(UUID.randomUUID(), "user", content, null));
    }
}
