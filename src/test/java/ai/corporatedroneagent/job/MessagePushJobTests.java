package ai.corporatedroneagent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
            job.queueAssistantReply(conversationId, "hello");

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
            job.queueAssistantReply(conversationId, "hello");

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
                job.queueAssistantReply(conversationId, "hang-" + index);
            }
            assertThat(hungCallsStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertEventually(() -> assertThat(interrupts.get()).isGreaterThanOrEqualTo(4));

            job.queueAssistantReply(conversationId, "after");

            verify(aiChatService, timeout(1000)).reply(conversationId, "after");
        } finally {
            keepBlocking.set(false);
            job.shutdown();
        }
    }

    @Test
    void lateAssistantRepliesArePublishedTransientlyAfterTimeoutInsteadOfBeingPersistedOutOfOrder()
            throws InterruptedException {
        UUID conversationId = UUID.randomUUID();
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
        MessagePushJob job = new MessagePushJob(
                conversationRepository,
                eventService,
                aiChatService,
                Duration.ofMillis(50)
        );

        try {
            job.queueAssistantReply(conversationId, "slow");
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            verify(eventService, timeout(1000).atLeast(2)).publish(eq("message-created"), any());

            finishLate.set(true);

            assertThat(lateReplyPublished.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupts.get()).isGreaterThanOrEqualTo(1);
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
        job.queueAssistantReply(conversationId, "hello");

        verify(eventService, timeout(1000).times(2)).publish(eq("message-created"), eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .map(MessageEventDto.class::cast)
                .extracting(event -> event.message().content())
                .anyMatch(content -> content.contains("reply pipeline is shutting down"));
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
            job.queueAssistantReply(conversationId, "hang-1");
            assertEventually(() -> assertThat(hungCallsStarted.getCount()).isEqualTo(1));
            verify(eventService, timeout(1000).atLeast(2)).publish(eq("message-created"), any());

            job.queueAssistantReply(conversationId, "hang-2");
            assertEventually(() -> assertThat(hungCallsStarted.getCount()).isZero());
            verify(eventService, timeout(1000).atLeast(4)).publish(eq("message-created"), any());

            job.queueAssistantReply(conversationId, "overflow");

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
}
