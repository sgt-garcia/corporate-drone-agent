package ai.corporatedroneagent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.corporatedroneagent.dto.MessageEventDto;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.service.AiChatService;
import ai.corporatedroneagent.service.ChatReply;
import ai.corporatedroneagent.service.EventService;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MessagePushJobTests {

    @Test
    void errorRepliesArePublishedButNotPersistedAsAssistantMessages() {
        UUID conversationId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.reply(conversationId, "hello"))
                .thenReturn(ChatReply.error("OpenAI request failed: timeout"));
        MessagePushJob job = new MessagePushJob(conversationRepository, eventService, aiChatService);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        try {
            queueAssistantReply(job, conversationId, "hello");

            verify(eventService, timeout(1000).times(2)).publish(eq("message-created"), eventCaptor.capture());
            assertThat(eventCaptor.getAllValues())
                    .map(MessageEventDto.class::cast)
                    .extracting(event -> event.message().role())
                    .containsExactly("status", "error");
            verify(conversationRepository, after(200).never()).appendMessage(eq(conversationId), any(Message.class));
        } finally {
            job.shutdown();
        }
    }

    @Test
    void movesConversationFromRunningToReviewWhenAReplyLands() {
        UUID conversationId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        when(conversationRepository.updateStatus(eq(conversationId), any())).thenReturn(true);
        when(conversationRepository.appendMessage(eq(conversationId), any(Message.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.reply(conversationId, "hi")).thenReturn(ChatReply.assistant("answer"));
        MessagePushJob job = new MessagePushJob(conversationRepository, eventService, aiChatService);

        try {
            queueAssistantReply(job, conversationId, "hi");

            // Gate on the reply landing (status + assistant message-created), then
            // assert the status moved running → review and never errored.
            verify(eventService, timeout(1000).times(2)).publish(eq("message-created"), any());
            verify(conversationRepository).updateStatus(conversationId, "running");
            verify(conversationRepository).updateStatus(conversationId, "review");
            verify(conversationRepository, never()).updateStatus(conversationId, "error");
        } finally {
            job.shutdown();
        }
    }

    @Test
    void movesConversationFromRunningToErrorWhenAReplyFails() {
        UUID conversationId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        when(conversationRepository.updateStatus(eq(conversationId), any())).thenReturn(true);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.reply(conversationId, "hi")).thenReturn(ChatReply.error("boom"));
        MessagePushJob job = new MessagePushJob(conversationRepository, eventService, aiChatService);

        try {
            queueAssistantReply(job, conversationId, "hi");

            // Gate on the failure landing (status + error message-created), then
            // assert the status moved running → error and never reached review.
            verify(eventService, timeout(1000).times(2)).publish(eq("message-created"), any());
            verify(conversationRepository).updateStatus(conversationId, "running");
            verify(conversationRepository).updateStatus(conversationId, "error");
            verify(conversationRepository, never()).updateStatus(conversationId, "review");
        } finally {
            job.shutdown();
        }
    }

    @Test
    void appendFailuresArePublishedAsErrorMessages() {
        UUID conversationId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.reply(conversationId, "hello"))
                .thenReturn(ChatReply.assistant("hi there"));
        doThrow(new IllegalStateException("database unavailable"))
                .when(conversationRepository)
                .appendMessage(eq(conversationId), any(Message.class));
        MessagePushJob job = new MessagePushJob(conversationRepository, eventService, aiChatService);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        try {
            queueAssistantReply(job, conversationId, "hello");

            verify(eventService, timeout(1000).times(2)).publish(eq("message-created"), eventCaptor.capture());
            assertThat(eventCaptor.getAllValues())
                    .map(MessageEventDto.class::cast)
                    .extracting(event -> event.message().role())
                    .containsExactly("status", "error");
            assertThat(((MessageEventDto) eventCaptor.getAllValues().get(1)).message().content())
                    .contains("Reply pipeline failed: database unavailable");
        } finally {
            job.shutdown();
        }
    }

    @Test
    void timedOutLlmCallsAreCancelledWithoutExhaustingFutureReplies() throws InterruptedException {
        UUID conversationId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        CountDownLatch hungCallsStarted = new CountDownLatch(4);
        AtomicBoolean keepBlocking = new AtomicBoolean(true);
        AtomicInteger interrupts = new AtomicInteger();
        when(aiChatService.reply(eq(conversationId), any(String.class))).thenAnswer(invocation -> {
            String content = invocation.getArgument(1, String.class);
            if ("after".equals(content)) {
                return ChatReply.assistant("still works");
            }

            hungCallsStarted.countDown();
            while (keepBlocking.get()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException exception) {
                    interrupts.incrementAndGet();
                }
            }
            return ChatReply.assistant("late");
        });
        MessagePushJob job = new MessagePushJob(
                conversationRepository,
                eventService,
                aiChatService,
                Duration.ofMillis(50)
        );

        try {
            for (int index = 0; index < 4; index++) {
                queueAssistantReply(job, conversationId, "hang-" + index);
            }
            assertThat(hungCallsStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertEventually(() -> assertThat(interrupts.get()).isGreaterThanOrEqualTo(4));

            queueAssistantReply(job, conversationId, "after");

            verify(aiChatService, timeout(1000)).reply(conversationId, "after");
        } finally {
            keepBlocking.set(false);
            job.shutdown();
        }
    }

    @Test
    void lateAssistantRepliesArePersistedWhenOriginalUserTurnIsStillLastPersistedMessage()
            throws InterruptedException {
        UUID conversationId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch lateReplyAppended = new CountDownLatch(1);
        AtomicBoolean finishLate = new AtomicBoolean(false);
        AtomicInteger interrupts = new AtomicInteger();
        when(aiChatService.reply(conversationId, "slow")).thenAnswer(invocation -> {
            started.countDown();
            while (!finishLate.get()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException exception) {
                    interrupts.incrementAndGet();
                }
            }
            return ChatReply.assistant("late answer");
        });
        when(conversationRepository.appendMessageIfLastUserMessageIs(
                eq(conversationId),
                eq(userMessageId),
                any(Message.class)
        )).thenAnswer(invocation -> {
            lateReplyAppended.countDown();
            return Optional.of(invocation.getArgument(2, Message.class));
        });
        MessagePushJob job = new MessagePushJob(
                conversationRepository,
                eventService,
                aiChatService,
                Duration.ofMillis(50)
        );
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        try {
            job.queueAssistantReply(conversationId, userMessageId, "slow");
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            verify(eventService, timeout(1000).atLeast(2)).publish(eq("message-created"), any());

            finishLate.set(true);

            assertThat(lateReplyAppended.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupts.get()).isGreaterThanOrEqualTo(1);
            verify(conversationRepository).appendMessageIfLastUserMessageIs(
                    eq(conversationId),
                    eq(userMessageId),
                    messageCaptor.capture()
            );
            assertThat(messageCaptor.getValue().getRole()).isEqualTo("assistant");
            assertThat(messageCaptor.getValue().getContent()).isEqualTo("late answer");
        } finally {
            finishLate.set(true);
            job.shutdown();
        }
    }

    @Test
    void lateAssistantRepliesAreTransientWhenNewerTurnsWouldMakePersistenceOutOfOrder()
            throws InterruptedException {
        UUID conversationId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch lateReplyPublished = new CountDownLatch(1);
        AtomicBoolean finishLate = new AtomicBoolean(false);
        AtomicInteger interrupts = new AtomicInteger();
        doAnswer(invocation -> {
            MessageEventDto event = invocation.getArgument(1, MessageEventDto.class);
            if ("assistant".equals(event.message().role())
                    && "late answer".equals(event.message().content())) {
                lateReplyPublished.countDown();
            }
            return null;
        }).when(eventService).publish(eq("message-created"), any());
        when(aiChatService.reply(conversationId, "slow")).thenAnswer(invocation -> {
            started.countDown();
            while (!finishLate.get()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException exception) {
                    interrupts.incrementAndGet();
                }
            }
            return ChatReply.assistant("late answer");
        });
        when(conversationRepository.appendMessageIfLastUserMessageIs(
                eq(conversationId),
                eq(userMessageId),
                any(Message.class)
        )).thenReturn(Optional.empty());
        MessagePushJob job = new MessagePushJob(
                conversationRepository,
                eventService,
                aiChatService,
                Duration.ofMillis(50)
        );

        try {
            job.queueAssistantReply(conversationId, userMessageId, "slow");
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            verify(eventService, timeout(1000).atLeast(2)).publish(eq("message-created"), any());

            finishLate.set(true);

            assertThat(lateReplyPublished.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupts.get()).isGreaterThanOrEqualTo(1);
            verify(conversationRepository)
                    .appendMessageIfLastUserMessageIs(eq(conversationId), eq(userMessageId), any(Message.class));
            verify(conversationRepository, after(200).never())
                    .appendMessage(eq(conversationId), any(Message.class));
        } finally {
            finishLate.set(true);
            job.shutdown();
        }
    }

    @Test
    void shutdownPublishesShutdownErrorInsteadOfProviderSaturationMessage() {
        UUID conversationId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        MessagePushJob job = new MessagePushJob(conversationRepository, eventService, aiChatService);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        job.shutdown();
        queueAssistantReply(job, conversationId, "hello");

        verify(eventService, timeout(1000)).publish(eq("message-created"), eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .map(MessageEventDto.class::cast)
                .extracting(event -> event.message().content())
                .anyMatch(content -> content.contains("reply pipeline is shutting down"));
        verify(aiChatService, after(200).never()).reply(eq(conversationId), any(String.class));
    }

    @Test
    void timeoutSchedulerRejectionDoesNotCallProvider() {
        UUID conversationId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        ExecutorService llmExecutor = Executors.newSingleThreadExecutor();
        ExecutorService replyExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService timeoutExecutor = mock(ScheduledExecutorService.class);
        when(timeoutExecutor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new RejectedExecutionException("scheduler closed"));
        MessagePushJob job = new MessagePushJob(
                conversationRepository,
                eventService,
                aiChatService,
                Duration.ofMillis(50),
                1,
                1,
                llmExecutor,
                replyExecutor,
                timeoutExecutor
        );
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        try {
            queueAssistantReply(job, conversationId, "hello");

            verify(aiChatService, after(200).never()).reply(eq(conversationId), any(String.class));
            verify(eventService, timeout(1000).times(2)).publish(eq("message-created"), eventCaptor.capture());
            assertThat(eventCaptor.getAllValues())
                    .map(MessageEventDto.class::cast)
                    .extracting(event -> event.message().content())
                    .anyMatch(content -> content.contains("scheduler closed"));
        } finally {
            job.shutdown();
        }
    }

    @Test
    void immediateTimeoutBeforeProviderCallDoesNotCallProvider() {
        UUID conversationId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        ExecutorService llmExecutor = Executors.newSingleThreadExecutor();
        ExecutorService replyExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService timeoutExecutor = mock(ScheduledExecutorService.class);
        when(timeoutExecutor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return mock(ScheduledFuture.class);
                });
        MessagePushJob job = new MessagePushJob(
                conversationRepository,
                eventService,
                aiChatService,
                Duration.ofMillis(50),
                1,
                1,
                llmExecutor,
                replyExecutor,
                timeoutExecutor
        );

        try {
            queueAssistantReply(job, conversationId, "hello");

            verify(aiChatService, after(200).never()).reply(eq(conversationId), any(String.class));
        } finally {
            job.shutdown();
        }
    }

    @Test
    void cancelledQueuedLlmTaskReleasesPermitsForLaterReplies() {
        UUID conversationId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        ExecutorService llmExecutor = mock(ExecutorService.class);
        ExecutorService replyExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger executedTasks = new AtomicInteger();
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0, Runnable.class);
            if (executedTasks.incrementAndGet() == 1) {
                ((java.util.concurrent.Future<?>) task).cancel(false);
            } else {
                task.run();
            }
            return null;
        }).when(llmExecutor).execute(any(Runnable.class));
        when(aiChatService.reply(conversationId, "after"))
                .thenReturn(ChatReply.error("visible after cancellation"));
        MessagePushJob job = new MessagePushJob(
                conversationRepository,
                eventService,
                aiChatService,
                Duration.ofSeconds(5),
                1,
                0,
                llmExecutor,
                replyExecutor,
                timeoutExecutor
        );
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        try {
            queueAssistantReply(job, conversationId, "cancelled");
            queueAssistantReply(job, conversationId, "after");

            verify(aiChatService, timeout(1000)).reply(conversationId, "after");
            verify(eventService, timeout(1000).atLeast(4)).publish(eq("message-created"), eventCaptor.capture());
            assertThat(eventCaptor.getAllValues())
                    .map(MessageEventDto.class::cast)
                    .extracting(event -> event.message().content())
                    .contains("visible after cancellation");
        } finally {
            job.shutdown();
        }
    }

    @Test
    void shutdownCancelsQueuedLlmTasksThatNeverStarted() {
        UUID conversationId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        ExecutorService llmExecutor = mock(ExecutorService.class);
        ExecutorService replyExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        doAnswer(invocation -> {
            queuedTask.set(invocation.getArgument(0, Runnable.class));
            return null;
        }).when(llmExecutor).execute(any(Runnable.class));
        MessagePushJob job = new MessagePushJob(
                conversationRepository,
                eventService,
                aiChatService,
                Duration.ofSeconds(5),
                1,
                0,
                llmExecutor,
                replyExecutor,
                timeoutExecutor
        );

        queueAssistantReply(job, conversationId, "queued");
        job.shutdown();

        assertThat(queuedTask.get()).isInstanceOf(java.util.concurrent.Future.class);
        assertThat(((java.util.concurrent.Future<?>) queuedTask.get()).isCancelled()).isTrue();
        verify(aiChatService, after(200).never()).reply(eq(conversationId), any(String.class));
    }

    @Test
    void interruptResistantTimedOutCallsAreCappedWithVisibleSaturationError() throws InterruptedException {
        UUID conversationId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        CountDownLatch hungCallsStarted = new CountDownLatch(2);
        AtomicBoolean keepBlocking = new AtomicBoolean(true);
        when(aiChatService.reply(eq(conversationId), any(String.class))).thenAnswer(invocation -> {
            String content = invocation.getArgument(1, String.class);
            if ("overflow".equals(content)) {
                return ChatReply.assistant("should not run");
            }
            hungCallsStarted.countDown();
            while (keepBlocking.get()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException exception) {
                    // Keep simulating a provider call that ignores Java task interruption.
                }
            }
            return ChatReply.assistant("late");
        });
        MessagePushJob job = new MessagePushJob(
                conversationRepository,
                eventService,
                aiChatService,
                Duration.ofMillis(50),
                1,
                1
        );
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        try {
            queueAssistantReply(job, conversationId, "hang-1");
            assertEventually(() -> assertThat(hungCallsStarted.getCount()).isEqualTo(1));
            verify(eventService, timeout(1000).atLeast(2)).publish(eq("message-created"), any());

            queueAssistantReply(job, conversationId, "hang-2");
            assertEventually(() -> assertThat(hungCallsStarted.getCount()).isZero());
            verify(eventService, timeout(1000).atLeast(4)).publish(eq("message-created"), any());

            queueAssistantReply(job, conversationId, "overflow");

            verify(aiChatService, after(200).never()).reply(conversationId, "overflow");
            verify(eventService, timeout(1000).atLeast(6)).publish(eq("message-created"), eventCaptor.capture());
            assertThat(eventCaptor.getAllValues())
                    .map(MessageEventDto.class::cast)
                    .extracting(event -> event.message().content())
                    .anyMatch(content -> content.contains("previous model requests are still stuck"));
        } finally {
            keepBlocking.set(false);
            job.shutdown();
        }
    }

    private static void assertEventually(Runnable assertion) throws InterruptedException {
        AssertionError lastError = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            try {
                assertion.run();
                return;
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(10);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    @Test
    void regenerateDropsTheReplacedReplyOnlyAfterTheReplacementPersists() {
        UUID conversationId = UUID.randomUUID();
        UUID replacedReplyId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        when(conversationRepository.updateStatus(eq(conversationId), any())).thenReturn(true);
        when(conversationRepository.appendMessage(eq(conversationId), any(Message.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));
        when(conversationRepository.deleteMessage(conversationId, replacedReplyId)).thenReturn(true);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.reply(conversationId, "hi")).thenReturn(ChatReply.assistant("fresh answer"));
        MessagePushJob job = jobWithSynchronousPipeline(conversationRepository, eventService, aiChatService);

        try {
            job.queueAssistantReply(conversationId, UUID.randomUUID(), "hi", replacedReplyId);

            // The replacement is persisted first, then the old reply is dropped.
            verify(conversationRepository).appendMessage(eq(conversationId), any(Message.class));
            verify(conversationRepository).deleteMessage(conversationId, replacedReplyId);
            verify(eventService).publish(eq("message-deleted"), any());
            verify(conversationRepository).updateStatus(conversationId, "review");
        } finally {
            job.shutdown();
        }
    }

    @Test
    void regenerateLeavesTheOriginalReplyIntactWhenTheReplacementFails() {
        UUID conversationId = UUID.randomUUID();
        UUID replacedReplyId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        when(conversationRepository.updateStatus(eq(conversationId), any())).thenReturn(true);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.reply(conversationId, "hi")).thenReturn(ChatReply.error("boom"));
        MessagePushJob job = jobWithSynchronousPipeline(conversationRepository, eventService, aiChatService);

        try {
            job.queueAssistantReply(conversationId, UUID.randomUUID(), "hi", replacedReplyId);

            // The failure lands, and the original reply is neither deleted nor
            // announced as deleted — it survives.
            verify(conversationRepository).updateStatus(conversationId, "error");
            verify(conversationRepository, never()).deleteMessage(conversationId, replacedReplyId);
            verify(conversationRepository, never()).appendMessage(eq(conversationId), any(Message.class));
            verify(eventService, never()).publish(eq("message-deleted"), any());
        } finally {
            job.shutdown();
        }
    }

    // Run the reply pipeline synchronously on the calling thread so these tests
    // are deterministic and don't depend on thread scheduling, which other tests
    // in this class can leave under load by hanging the provider until shutdown.
    // The reply has fully landed by the time queueAssistantReply returns.
    private static MessagePushJob jobWithSynchronousPipeline(
            ConversationRepository conversationRepository,
            EventService eventService,
            AiChatService aiChatService
    ) {
        return new MessagePushJob(
                conversationRepository,
                eventService,
                aiChatService,
                Duration.ofSeconds(60),
                4,
                4,
                directExecutor(),
                directExecutor(),
                Executors.newSingleThreadScheduledExecutor()
        );
    }

    private static ExecutorService directExecutor() {
        return new AbstractExecutorService() {
            private volatile boolean shutdown;

            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {
                shutdown = true;
            }

            @Override
            public java.util.List<Runnable> shutdownNow() {
                shutdown = true;
                return java.util.List.of();
            }

            @Override
            public boolean isShutdown() {
                return shutdown;
            }

            @Override
            public boolean isTerminated() {
                return shutdown;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }
        };
    }

    private static void queueAssistantReply(MessagePushJob job, UUID conversationId, String userContent) {
        job.queueAssistantReply(conversationId, UUID.randomUUID(), userContent);
    }
}
