package ai.corporatedroneagent.job;

import ai.corporatedroneagent.dto.MessageDto;
import ai.corporatedroneagent.dto.MessageEventDto;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.service.AiChatService;
import ai.corporatedroneagent.service.ChatReply;
import ai.corporatedroneagent.service.EventService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MessagePushJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessagePushJob.class);
    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(60);
    private static final String TIMEOUT_REPLY =
            "The model did not respond within 60 seconds. Please try again, or check whether the selected provider is running.";

    private final ConversationRepository conversationRepository;
    private final EventService eventService;
    private final AiChatService aiChatService;
    private final ExecutorService llmExecutor;
    private final ExecutorService replyExecutor;
    private final ScheduledExecutorService timeoutExecutor;
    private final Duration llmTimeout;

    @Autowired
    public MessagePushJob(
            ConversationRepository conversationRepository,
            EventService eventService,
            AiChatService aiChatService
    ) {
        this(conversationRepository, eventService, aiChatService, LLM_TIMEOUT);
    }

    MessagePushJob(
            ConversationRepository conversationRepository,
            EventService eventService,
            AiChatService aiChatService,
            Duration llmTimeout
    ) {
        this.conversationRepository = conversationRepository;
        this.eventService = eventService;
        this.aiChatService = aiChatService;
        this.llmExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("cda-llm-", 1).factory());
        this.replyExecutor = Executors.newSingleThreadExecutor(namedThreadFactory("cda-reply"));
        this.timeoutExecutor = Executors.newSingleThreadScheduledExecutor(namedThreadFactory("cda-llm-timeout"));
        this.llmTimeout = llmTimeout;
    }

    public void queueAssistantReply(UUID conversationId, String userContent) {
        publishStatus(conversationId);

        requestReply(conversationId, userContent)
                .exceptionally(error -> ChatReply.error("LLM request failed: " + rootMessage(error)))
                .thenAcceptAsync(reply -> publishReply(conversationId, reply), replyExecutor)
                .exceptionally(error -> {
                    publishPipelineFailure(conversationId, error);
                    return null;
                });
    }

    @PreDestroy
    public void shutdown() {
        llmExecutor.shutdownNow();
        replyExecutor.shutdownNow();
        timeoutExecutor.shutdownNow();
    }

    private CompletableFuture<ChatReply> requestReply(UUID conversationId, String userContent) {
        CompletableFuture<ChatReply> replyFuture = new CompletableFuture<>();
        Future<?> llmTask;
        try {
            llmTask = llmExecutor.submit(() -> {
                try {
                    replyFuture.complete(aiChatService.reply(conversationId, userContent));
                } catch (Throwable error) {
                    replyFuture.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException exception) {
            replyFuture.completeExceptionally(exception);
            return replyFuture;
        }

        ScheduledFuture<?> timeoutTask = timeoutExecutor.schedule(
                () -> timeoutReply(conversationId, replyFuture, llmTask),
                Math.max(1L, llmTimeout.toMillis()),
                TimeUnit.MILLISECONDS
        );
        replyFuture.whenComplete((reply, error) -> timeoutTask.cancel(false));
        return replyFuture;
    }

    private void timeoutReply(UUID conversationId, CompletableFuture<ChatReply> replyFuture, Future<?> llmTask) {
        if (replyFuture.complete(ChatReply.error(TIMEOUT_REPLY))) {
            llmTask.cancel(true);
            LOGGER.warn("LLM request timed out after {} ms for conversation {}.", llmTimeout.toMillis(), conversationId);
        }
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

    private void publishPipelineFailure(UUID conversationId, Throwable error) {
        LOGGER.error("Reply pipeline failed for conversation {}.", conversationId, error);
        try {
            publishTransientMessage(conversationId, "error", "Reply pipeline failed: " + rootMessage(error));
        } catch (RuntimeException publishError) {
            LOGGER.error("Failed to publish reply pipeline failure for conversation {}.", conversationId, publishError);
        }
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
