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
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MessagePushJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessagePushJob.class);
    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_ACTIVE_LLM_REQUESTS = 4;
    private static final int MAX_TIMED_OUT_LLM_REQUESTS = 4;
    private static final String TIMEOUT_REPLY =
            "The model did not respond within 60 seconds. Please try again, or check whether the selected provider is running.";
    private static final String BUSY_REPLY =
            "The model is already processing several replies. Please wait for one to finish and try again.";
    private static final String SATURATED_REPLY =
            "Several previous model requests are still stuck after timing out. Please wait a moment, or restart the app if the provider is not recovering.";
    private static final String SHUTTING_DOWN_REPLY =
            "The reply pipeline is shutting down. Please try again after the app finishes restarting.";

    private final ConversationRepository conversationRepository;
    private final EventService eventService;
    private final AiChatService aiChatService;
    private final ExecutorService llmExecutor;
    private final ExecutorService replyExecutor;
    private final ScheduledExecutorService timeoutExecutor;
    private final Duration llmTimeout;
    private final Semaphore activeRequests;
    private final Semaphore physicalRequests;
    private volatile boolean shuttingDown;

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
        this(
                conversationRepository,
                eventService,
                aiChatService,
                llmTimeout,
                MAX_ACTIVE_LLM_REQUESTS,
                MAX_TIMED_OUT_LLM_REQUESTS
        );
    }

    MessagePushJob(
            ConversationRepository conversationRepository,
            EventService eventService,
            AiChatService aiChatService,
            Duration llmTimeout,
            int maxActiveRequests,
            int maxTimedOutRequests
    ) {
        this(
                conversationRepository,
                eventService,
                aiChatService,
                llmTimeout,
                maxActiveRequests,
                maxTimedOutRequests,
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("cda-llm-", 1).factory()),
                Executors.newSingleThreadExecutor(namedThreadFactory("cda-reply")),
                Executors.newSingleThreadScheduledExecutor(namedThreadFactory("cda-llm-timeout"))
        );
    }

    MessagePushJob(
            ConversationRepository conversationRepository,
            EventService eventService,
            AiChatService aiChatService,
            Duration llmTimeout,
            int maxActiveRequests,
            int maxTimedOutRequests,
            ExecutorService llmExecutor,
            ExecutorService replyExecutor,
            ScheduledExecutorService timeoutExecutor
    ) {
        this.conversationRepository = conversationRepository;
        this.eventService = eventService;
        this.aiChatService = aiChatService;
        this.llmExecutor = llmExecutor;
        this.replyExecutor = replyExecutor;
        this.timeoutExecutor = timeoutExecutor;
        this.llmTimeout = llmTimeout;
        this.activeRequests = new Semaphore(maxActiveRequests);
        this.physicalRequests = new Semaphore(maxActiveRequests + maxTimedOutRequests);
    }

    public void queueAssistantReply(UUID conversationId, String userContent) {
        queueAssistantReply(conversationId, null, userContent);
    }

    public void queueAssistantReply(UUID conversationId, UUID userMessageId, String userContent) {
        if (shuttingDown) {
            publishTransientMessage(conversationId, "error", SHUTTING_DOWN_REPLY);
            return;
        }
        publishStatus(conversationId);

        requestReply(conversationId, userMessageId, userContent)
                .exceptionally(error -> ChatReply.error("LLM request failed: " + rootMessage(error)))
                .thenAcceptAsync(reply -> publishReply(conversationId, reply), replyExecutor)
                .exceptionally(error -> {
                    publishPipelineFailure(conversationId, error);
                    return null;
                });
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        llmExecutor.shutdownNow();
        replyExecutor.shutdownNow();
        timeoutExecutor.shutdownNow();
    }

    private CompletableFuture<ChatReply> requestReply(UUID conversationId, UUID userMessageId, String userContent) {
        CompletableFuture<ChatReply> replyFuture = new CompletableFuture<>();
        if (shuttingDown) {
            replyFuture.complete(ChatReply.error(SHUTTING_DOWN_REPLY));
            return replyFuture;
        }
        if (!physicalRequests.tryAcquire()) {
            replyFuture.complete(ChatReply.error(SATURATED_REPLY));
            return replyFuture;
        }
        if (!activeRequests.tryAcquire()) {
            physicalRequests.release();
            replyFuture.complete(ChatReply.error(BUSY_REPLY));
            return replyFuture;
        }

        AtomicBoolean activePermitHeld = new AtomicBoolean(true);
        AtomicBoolean physicalPermitHeld = new AtomicBoolean(true);
        AtomicReference<Future<?>> llmTask = new AtomicReference<>();
        ScheduledFuture<?> timeoutTask;
        try {
            timeoutTask = timeoutExecutor.schedule(
                    () -> timeoutReply(conversationId, replyFuture, llmTask.get()),
                    Math.max(1L, llmTimeout.toMillis()),
                    TimeUnit.MILLISECONDS
            );
        } catch (RejectedExecutionException exception) {
            releaseActivePermit(activePermitHeld);
            releasePhysicalPermit(physicalPermitHeld);
            replyFuture.completeExceptionally(exception);
            return replyFuture;
        }

        try {
            Future<?> submittedTask = llmExecutor.submit(() -> {
                try {
                    ChatReply reply = aiChatService.reply(conversationId, userContent);
                    if (!replyFuture.complete(reply)) {
                        publishLateReply(conversationId, userMessageId, reply);
                    }
                } catch (Throwable error) {
                    if (!replyFuture.completeExceptionally(error)) {
                        LOGGER.warn(
                                "LLM request completed with an error after the reply pipeline moved on for conversation {}.",
                                conversationId,
                                error
                        );
                    }
                } finally {
                    releasePhysicalPermit(physicalPermitHeld);
                }
            });
            llmTask.set(submittedTask);
            if (replyFuture.isDone()) {
                submittedTask.cancel(true);
            }
        } catch (RejectedExecutionException exception) {
            timeoutTask.cancel(false);
            releaseActivePermit(activePermitHeld);
            releasePhysicalPermit(physicalPermitHeld);
            replyFuture.completeExceptionally(exception);
            return replyFuture;
        }

        replyFuture.whenComplete((reply, error) -> {
            releaseActivePermit(activePermitHeld);
            timeoutTask.cancel(false);
        });
        return replyFuture;
    }

    private void timeoutReply(UUID conversationId, CompletableFuture<ChatReply> replyFuture, Future<?> llmTask) {
        if (replyFuture.complete(ChatReply.error(TIMEOUT_REPLY))) {
            if (llmTask != null) {
                llmTask.cancel(true);
            }
            LOGGER.warn("LLM request timed out after {} ms for conversation {}.", llmTimeout.toMillis(), conversationId);
        }
    }

    private void releaseActivePermit(AtomicBoolean activePermitHeld) {
        if (activePermitHeld.compareAndSet(true, false)) {
            activeRequests.release();
        }
    }

    private void releasePhysicalPermit(AtomicBoolean physicalPermitHeld) {
        if (physicalPermitHeld.compareAndSet(true, false)) {
            physicalRequests.release();
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

    private void publishLateReply(UUID conversationId, UUID userMessageId, ChatReply reply) {
        if (!reply.assistant()) {
            LOGGER.warn("LLM request returned a late {} reply for conversation {} after the pipeline moved on.",
                    reply.role(), conversationId);
            return;
        }
        LOGGER.info("Publishing late assistant reply for conversation {} after an earlier timeout.", conversationId);
        try {
            CompletableFuture
                    .runAsync(() -> publishLateAssistantReply(conversationId, userMessageId, reply), replyExecutor)
                    .exceptionally(error -> {
                        publishPipelineFailure(conversationId, error);
                        return null;
                    });
        } catch (RejectedExecutionException exception) {
            LOGGER.warn("Late assistant reply could not be published because the reply executor is shutting down.");
        }
    }

    private void publishLateAssistantReply(UUID conversationId, UUID userMessageId, ChatReply reply) {
        if (canAppendLateReply(conversationId, userMessageId)) {
            appendAssistantMessage(conversationId, reply.content());
            return;
        }
        publishTransientMessage(conversationId, reply.role(), reply.content());
    }

    private boolean canAppendLateReply(UUID conversationId, UUID userMessageId) {
        if (userMessageId == null) {
            return false;
        }
        return conversationRepository.findById(conversationId)
                .map(conversation -> {
                    if (conversation.getMessages().isEmpty()) {
                        return false;
                    }
                    Message lastMessage = conversation.getMessages().get(conversation.getMessages().size() - 1);
                    return userMessageId.equals(lastMessage.getId()) && "user".equals(lastMessage.getRole());
                })
                .orElse(false);
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
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
