package ai.corporatedroneagent.job;

import ai.corporatedroneagent.dto.MessageDto;
import ai.corporatedroneagent.dto.MessageEventDto;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.service.AiChatService;
import ai.corporatedroneagent.service.EventService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

@Component
public class MessagePushJob {

    private final ConversationRepository conversationRepository;
    private final EventService eventService;
    private final AiChatService aiChatService;

    public MessagePushJob(
            ConversationRepository conversationRepository,
            EventService eventService,
            AiChatService aiChatService
    ) {
        this.conversationRepository = conversationRepository;
        this.eventService = eventService;
        this.aiChatService = aiChatService;
    }

    public void queueAssistantReply(UUID conversationId, String userContent) {
        CompletableFuture.runAsync(() -> {
            pause(500);
            publishStatus(conversationId);
            pause(700);
            appendAssistantMessage(conversationId, userContent);
        });
    }

    private void publishStatus(UUID conversationId) {
        MessageDto status = new MessageDto(
                UUID.randomUUID(),
                "status",
                "CDA is processing...",
                Instant.now()
        );
        eventService.publish("message-created", new MessageEventDto(conversationId, status));
    }

    private void appendAssistantMessage(UUID conversationId, String userContent) {
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            Message message = new Message(
                    UUID.randomUUID(),
                    "assistant",
                    aiChatService.reply(userContent),
                    Instant.now()
            );
            conversation.getMessages().add(message);
            conversationRepository.save(conversation);
            MessageDto dto = new MessageDto(
                    message.getId(),
                    message.getRole(),
                    message.getContent(),
                    message.getCreatedAt()
            );
            eventService.publish("message-created", new MessageEventDto(conversationId, dto));
        });
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
