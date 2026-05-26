package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ConversationDto;
import ai.corporatedroneagent.dto.ConversationRequest;
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

    public synchronized ConversationDto create(UUID projectId, ConversationRequest request) {
        Project project = getProject(projectId);
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setProjectId(projectId);
        conversation.setName(Strings.defaultIfBlank(request.name(), "New Conversation"));
        conversation.getMessages().add(new Message(
                UUID.randomUUID(),
                "assistant",
                "Ready for **" + conversation.getName() + "** in " + project.getName() + ".",
                Instant.now()
        ));
        conversationRepository.save(conversation);

        project.getConversationIds().add(conversation.getId());
        projectRepository.save(project);

        ConversationDto dto = toDto(conversation);
        eventService.publish("conversation-created", new ConversationSummaryDto(
                conversation.getId(),
                conversation.getProjectId(),
                conversation.getName()
        ));
        return dto;
    }

    public synchronized ConversationDto update(UUID conversationId, ConversationRequest request) {
        Conversation conversation = getConversation(conversationId);
        conversation.setName(Strings.defaultIfBlank(request.name(), conversation.getName()));
        conversationRepository.save(conversation);
        ConversationDto dto = toDto(conversation);
        eventService.publish("conversation-updated", dto);
        return dto;
    }

    public synchronized MessageDto sendUserMessage(UUID conversationId, String content) {
        Conversation conversation = getConversation(conversationId);
        Message message = new Message(
                UUID.randomUUID(),
                "user",
                Strings.defaultIfBlank(content, ""),
                Instant.now()
        );

        if (message.getContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content is required");
        }

        conversation.getMessages().add(message);
        conversationRepository.save(conversation);
        MessageDto dto = toDto(message);
        eventService.publish("message-created", new MessageEventDto(conversationId, dto));
        messagePushJob.queueAssistantReply(conversationId, message.getContent());
        return dto;
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
                conversation.getMessages().stream().map(this::toDto).toList()
        );
    }

    private MessageDto toDto(Message message) {
        return new MessageDto(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
    }
}
