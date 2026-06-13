package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ConversationDto;
import ai.corporatedroneagent.dto.ConversationRequest;
import ai.corporatedroneagent.dto.ConversationStatusDto;
import ai.corporatedroneagent.dto.ConversationSummaryDto;
import ai.corporatedroneagent.dto.MessageDto;
import ai.corporatedroneagent.dto.MessageEventDto;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.model.Project;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.repository.ProjectRepository;
import ai.corporatedroneagent.util.Strings;
import ai.corporatedroneagent.job.MessagePushJob;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ProjectRepository projectRepository;
    private final EventService eventService;
    private final MessagePushJob messagePushJob;

    public ConversationService(
            ConversationRepository conversationRepository,
            ProjectRepository projectRepository,
            EventService eventService,
            MessagePushJob messagePushJob
    ) {
        this.conversationRepository = conversationRepository;
        this.projectRepository = projectRepository;
        this.eventService = eventService;
        this.messagePushJob = messagePushJob;
    }

    public synchronized ConversationDto get(UUID conversationId) {
        return toDto(getConversation(conversationId));
    }

    /**
     * Acknowledge a freshly-completed conversation: review → success. Triggered
     * when the user opens it. A no-op for any other status, so callers can fire
     * it on every open without checking first.
     */
    public synchronized void markSeen(UUID conversationId) {
        // Compare-and-set review → success in a single statement, so the only
        // residual race (a late, post-timeout reply writing while status is
        // review) resolves atomically rather than via a read-then-write. Publish
        // only when this call is the one that moved the row.
        if (conversationRepository.updateStatusIf(conversationId, "review", "success")) {
            eventService.publish("conversation-status", new ConversationStatusDto(conversationId, "success"));
        }
    }

    public synchronized ConversationDto create(UUID projectId, ConversationRequest request) {
        Project project = getProject(projectId);
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setProjectId(projectId);
        conversation.setName(Strings.defaultIfBlank(request.name(), "New Conversation"));
        conversationRepository.save(conversation);

        // New conversations surface at the top of the project, matching the design.
        project.getConversationIds().add(0, conversation.getId());
        projectRepository.save(project);

        ConversationDto dto = toDto(conversation);
        eventService.publish("conversation-created", new ConversationSummaryDto(
                conversation.getId(),
                conversation.getProjectId(),
                conversation.getName(),
                conversation.getStatus()
        ));
        return dto;
    }

    public synchronized ConversationDto update(UUID conversationId, ConversationRequest request) {
        Conversation conversation = getConversation(conversationId);
        conversation.setName(Strings.defaultIfBlank(request.name(), conversation.getName()));
        conversationRepository.save(conversation);
        ConversationDto dto = toDto(conversation);
        eventService.publish("conversation-updated", Map.of("id", conversationId));
        return dto;
    }

    public synchronized void delete(UUID conversationId) {
        Conversation conversation = getConversation(conversationId);
        UUID projectId = conversation.getProjectId();

        projectRepository.findById(projectId).ifPresent(project -> {
            project.getConversationIds().remove(conversationId);
            projectRepository.save(project);
        });
        conversationRepository.delete(conversationId);

        eventService.publish("conversation-deleted", new ConversationSummaryDto(
                conversationId,
                projectId,
                conversation.getName(),
                conversation.getStatus()
        ));
    }

    public synchronized MessageDto sendUserMessage(UUID conversationId, String content) {
        Message message = new Message(
                UUID.randomUUID(),
                "user",
                Strings.defaultIfBlank(content, ""),
                Instant.now()
        );

        if (message.getContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content is required");
        }

        conversationRepository.appendMessage(conversationId, message)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        MessageDto dto = toDto(message);
        eventService.publish("message-created", new MessageEventDto(conversationId, dto));
        messagePushJob.queueAssistantReply(conversationId, message.getId(), message.getContent());
        return dto;
    }

    /**
     * Re-run the reply for the most recent user message. A failed reply is a
     * transient turn (it is published over SSE but never persisted), so there is
     * nothing to delete server-side — we simply re-queue the assistant reply and
     * let it stream back in place of the dropped error turn.
     */
    public synchronized void retryLastReply(UUID conversationId) {
        Conversation conversation = getConversation(conversationId);
        if (replyInFlight(conversation)) {
            return;
        }
        Message lastUserMessage = lastUserMessage(conversation)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "There is no message to retry yet"));
        messagePushJob.queueAssistantReply(conversationId, lastUserMessage.getId(), lastUserMessage.getContent());
    }

    /**
     * Regenerate the most recent assistant reply in place. Unlike {@link
     * #retryLastReply}, the reply being replaced here is a <em>persisted</em>
     * assistant turn. We hand its id to the reply pipeline rather than deleting it
     * up front: the old reply is dropped (publishing a {@code message-deleted}
     * event so connected clients swap it) only once the replacement has been
     * persisted. If the regenerated reply errors it is never persisted, so the
     * original reply is left untouched rather than silently lost. The status
     * indicator and the new reply stream back over SSE, swapping the turn rather
     * than appending a duplicate. If the last reply never persisted (e.g. it
     * errored), there is nothing to replace and this behaves like {@link
     * #retryLastReply}.
     */
    public synchronized void regenerateLastReply(UUID conversationId) {
        Conversation conversation = getConversation(conversationId);
        if (replyInFlight(conversation)) {
            return;
        }
        Message lastUserMessage = lastUserMessage(conversation)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "There is no reply to regenerate yet"));
        UUID replacedReplyId = trailingAssistantReplyId(conversation).orElse(null);
        messagePushJob.queueAssistantReply(
                conversationId, lastUserMessage.getId(), lastUserMessage.getContent(), replacedReplyId);
    }

    // A reply is already being generated for this conversation. queueAssistantReply
    // flips the status to "running" synchronously (publishStatus) before the async
    // pipeline starts, and retry/regenerate share this service's monitor, so a
    // second rapid request — e.g. a double-clicked button — sees "running" and is
    // dropped rather than stacking a duplicate reply onto the same turn.
    private boolean replyInFlight(Conversation conversation) {
        return "running".equals(conversation.getStatus());
    }

    private Optional<UUID> trailingAssistantReplyId(Conversation conversation) {
        List<Message> messages = conversation.getMessages();
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        Message last = messages.get(messages.size() - 1);
        if (!"assistant".equals(last.getRole())) {
            return Optional.empty();
        }
        return Optional.of(last.getId());
    }

    private Optional<Message> lastUserMessage(Conversation conversation) {
        List<Message> messages = conversation.getMessages();
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            if ("user".equals(message.getRole())) {
                return Optional.of(message);
            }
        }
        return Optional.empty();
    }

    private Project getProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private Conversation getConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private ConversationDto toDto(Conversation conversation) {
        return new ConversationDto(
                conversation.getId(),
                conversation.getProjectId(),
                conversation.getName(),
                conversation.getStatus(),
                conversation.getMessages().stream().map(this::toDto).toList()
        );
    }

    private MessageDto toDto(Message message) {
        return new MessageDto(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt(),
                message.getSources());
    }
}
