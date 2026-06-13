package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.dto.ApiEvent;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EventServiceTests {

    @Test
    void rejectedMessageCreatedEventRunsOnCallerThread() {
        AtomicInteger sends = new AtomicInteger();
        ThreadPoolExecutor executor = executor();
        try {
            new EventService.EventRejectionHandler().rejectedExecution(
                    new EventService.EventSendTask(
                            new ApiEvent("message-created", Map.of()),
                            sends::incrementAndGet
                    ),
                    executor
            );

            assertThat(sends).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectedMessageDeletedEventRunsOnCallerThread() {
        AtomicInteger sends = new AtomicInteger();
        ThreadPoolExecutor executor = executor();
        try {
            new EventService.EventRejectionHandler().rejectedExecution(
                    new EventService.EventSendTask(
                            new ApiEvent("message-deleted", Map.of()),
                            sends::incrementAndGet
                    ),
                    executor
            );

            assertThat(sends).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectedConversationStatusEventRunsOnCallerThread() {
        AtomicInteger sends = new AtomicInteger();
        ThreadPoolExecutor executor = executor();
        try {
            new EventService.EventRejectionHandler().rejectedExecution(
                    new EventService.EventSendTask(
                            new ApiEvent("conversation-status", Map.of()),
                            sends::incrementAndGet
                    ),
                    executor
            );

            assertThat(sends).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectedRefetchNotificationIsDiscarded() {
        AtomicInteger sends = new AtomicInteger();
        ThreadPoolExecutor executor = executor();
        try {
            new EventService.EventRejectionHandler().rejectedExecution(
                    new EventService.EventSendTask(
                            new ApiEvent("projects-updated", Map.of()),
                            sends::incrementAndGet
                    ),
                    executor
            );

            assertThat(sends).hasValue(0);
        } finally {
            executor.shutdownNow();
        }
    }

    private ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1)
        );
    }
}
