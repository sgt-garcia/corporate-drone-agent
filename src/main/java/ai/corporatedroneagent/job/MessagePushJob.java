package ai.corporatedroneagent.job;

import ai.corporatedroneagent.dto.ConversationStatusDto;
import ai.corporatedroneagent.dto.MessageDto;
import ai.corporatedroneagent.dto.MessageEventDto;
import ai.corporatedroneagent.dto.MessageSourceDto;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.service.AiChatService;
import ai.corporatedroneagent.service.ChatReply;
import ai.corporatedroneagent.service.EventService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Set<FutureTask<?>> queuedLlmTasks = ConcurrentHashMap.newKeySet();
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
        queuedLlmTasks.forEach(task -> task.cancel(true));
        llmExecutor.shutdownNow();
        queuedLlmTasks.forEach(task -> task.cancel(true));
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
        AtomicBoolean taskStarted = new AtomicBoolean(false);
        AtomicReference<FutureTask<Void>> llmTask = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> timeoutTask = new AtomicReference<>();
        Callable<Void> request = () -> {
            taskStarted.set(true);
            queuedLlmTasks.remove(llmTask.get());
            ScheduledFuture<?> scheduledTimeout = null;
            try {
                if (replyFuture.isDone()) {
                    return null;
                }
                scheduledTimeout = scheduleTimeout(
                        conversationId,
                        replyFuture,
                        llmTask.get()
                );
                timeoutTask.set(scheduledTimeout);
                if (replyFuture.isDone()) {
                    return null;
                }
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
                if (scheduledTimeout != null) {
                    scheduledTimeout.cancel(false);
                }
                releasePhysicalPermit(physicalPermitHeld);
            }
            return null;
        };
        FutureTask<Void> task = new FutureTask<>(request) {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                boolean cancelled = super.cancel(mayInterruptIfRunning);
                if (cancelled && !taskStarted.get()) {
                    queuedLlmTasks.remove(this);
                    releasePhysicalPermit(physicalPermitHeld);
                    replyFuture.completeExceptionally(
                            new CancellationException("LLM request was cancelled before starting")
                    );
                }
                return cancelled;
            }
        };
        llmTask.set(task);
        queuedLlmTasks.add(task);
        try {
            llmExecutor.execute(task);
            if (replyFuture.isDone()) {
                task.cancel(true);
            }
        } catch (RejectedExecutionException exception) {
            queuedLlmTasks.remove(task);
            releaseActivePermit(activePermitHeld);
            releasePhysicalPermit(physicalPermitHeld);
            replyFuture.completeExceptionally(exception);
            return replyFuture;
        }

        replyFuture.whenComplete((reply, error) -> {
            releaseActivePermit(activePermitHeld);
            ScheduledFuture<?> scheduledTimeout = timeoutTask.get();
            if (scheduledTimeout != null) {
                scheduledTimeout.cancel(false);
            }
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

    private ScheduledFuture<?> scheduleTimeout(
            UUID conversationId,
            CompletableFuture<ChatReply> replyFuture,
            Future<?> llmTask
    ) {
        return timeoutExecutor.schedule(
                () -> timeoutReply(conversationId, replyFuture, llmTask),
                Math.max(1L, llmTimeout.toMillis()),
                TimeUnit.MILLISECONDS
        );
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
                Instant.now(),
                List.of()
        );
        eventService.publish("message-created", new MessageEventDto(conversationId, status));
        setConversationStatus(conversationId, "running");
    }

    // Persist the conversation's run status and notify the sidebar. States:
    // running (reply in flight) → review (reply landed) | error (reply failed).
    private void setConversationStatus(UUID conversationId, String status) {
        if (conversationRepository.updateStatus(conversationId, status)) {
            eventService.publish("conversation-status", new ConversationStatusDto(conversationId, status));
        }
    }

    private void publishReply(UUID conversationId, ChatReply reply) {
        if (reply.assistant()) {
            appendAssistantMessage(conversationId, reply.content(), reply.sources());
            return;
        }
        publishTransientMessage(conversationId, reply.role(), reply.content());
    }

    private void appendAssistantMessage(UUID conversationId, String content, List<MessageSourceDto> sources) {
        Message message = assistantMessage(content, sources);

        conversationRepository.appendMessage(conversationId, message)
                .ifPresent(savedMessage -> {
                    eventService.publish(
                            "message-created",
                            new MessageEventDto(conversationId, toDto(savedMessage))
                    );
                    setConversationStatus(conversationId, "review");
                });
    }

    private void publishTransientMessage(UUID conversationId, String role, String content) {
        MessageDto message = new MessageDto(
                UUID.randomUUID(),
                role,
                content,
                Instant.now(),
                List.of()
        );
        eventService.publish("message-created", new MessageEventDto(conversationId, message));
        // Transient messages are always failure replies (error/busy/saturated/
        // shutting-down), so the conversation lands in the error state.
        setConversationStatus(conversationId, "error");
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
        if (userMessageId == null) {
            publishTransientMessage(conversationId, reply.role(), reply.content());
            return;
        }
        Message message = assistantMessage(reply.content(), reply.sources());
        conversationRepository.appendMessageIfLastUserMessageIs(conversationId, userMessageId, message)
                .ifPresentOrElse(
                        savedMessage -> {
                            eventService.publish(
                                    "message-created",
                                    new MessageEventDto(conversationId, toDto(savedMessage))
                            );
                            setConversationStatus(conversationId, "review");
                        },
                        () -> publishTransientMessage(conversationId, reply.role(), reply.content())
                );
    }

    private Message assistantMessage(String content, List<MessageSourceDto> sources) {
        Message message = new Message(
                UUID.randomUUID(),
                "assistant",
                content,
                Instant.now()
        );
        message.setSources(sources);
        return message;
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
                message.getCreatedAt(),
                message.getSources()
        );
    }

}
