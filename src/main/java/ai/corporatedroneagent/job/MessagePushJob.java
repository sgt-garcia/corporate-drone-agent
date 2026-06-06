package ai.corporatedroneagent.job;

import ai.corporatedroneagent.dto.MessageDto;
import ai.corporatedroneagent.dto.MessageEventDto;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.service.AiChatService;
import ai.corporatedroneagent.service.ChatReply;
import ai.corporatedroneagent.service.EventService;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class MessagePushJob {

    private static final long LLM_TIMEOUT_SECONDS = 60;
    private static final String TIMEOUT_REPLY =
            "The model did not respond within 60 seconds. Please try again, or check whether the selected provider is running.";

    private final ConversationRepository conversationRepository;
    private final EventService eventService;
    private final AiChatService aiChatService;
    private final ExecutorService llmExecutor;
    private final ExecutorService replyExecutor;

    public MessagePushJob(
            ConversationRepository conversationRepository,
            EventService eventService,
            AiChatService aiChatService
    ) {
        this.conversationRepository = conversationRepository;
        this.eventService = eventService;
        this.aiChatService = aiChatService;
        this.llmExecutor = Executors.newFixedThreadPool(4, namedThreadFactory("cda-llm"));
        this.replyExecutor = Executors.newSingleThreadExecutor(namedThreadFactory("cda-reply"));
    }

    public void queueAssistantReply(UUID conversationId, String userContent) {
        publishStatus(conversationId);

        CompletableFuture
                .supplyAsync(() -> aiChatService.reply(conversationId, userContent), llmExecutor)
                .completeOnTimeout(ChatReply.error(TIMEOUT_REPLY), LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(error -> ChatReply.error("LLM request failed: " + rootMessage(error)))
                .thenAcceptAsync(reply -> publishReply(conversationId, reply), replyExecutor);
    }

    @PreDestroy
    public void shutdown() {
        llmExecutor.shutdownNow();
        replyExecutor.shutdownNow();
    }

    private void publishStatus(UUID conversationId) {
        MessageDto status = new MessageDto(
                UUID.randomUUID(),
                "status",
                "...",
                Instant.now()
        );
        eventService.publish("message-created", new MessageEventDto(conversationId, status));
    }

    private void publishReply(UUID conversationId, ChatReply reply) {
        if (reply.assistant()) {
            appendAssistantMessage(conversationId, reply.content());
            return;
        }
        publishTransientMessage(conversationId, reply.role(), reply.content());
    }

    private void appendAssistantMessage(UUID conversationId, String content) {
        Message message = new Message(
                UUID.randomUUID(),
                "assistant",
                content,
                Instant.now()
        );

        conversationRepository.appendMessage(conversationId, message)
                .ifPresent(savedMessage -> eventService.publish(
                        "message-created",
                        new MessageEventDto(conversationId, toDto(message))
                ));
    }

    private void publishTransientMessage(UUID conversationId, String role, String content) {
        MessageDto message = new MessageDto(
                UUID.randomUUID(),
                role,
                content,
                Instant.now()
        );
        eventService.publish("message-created", new MessageEventDto(conversationId, message));
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private MessageDto toDto(Message message) {
        return new MessageDto(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }

}
