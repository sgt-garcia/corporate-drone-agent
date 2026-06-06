package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ApiEvent;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class EventService {

    private static final int EVENT_THREADS = 2;
    private static final int EVENT_QUEUE_CAPACITY = 256;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ThreadPoolExecutor eventExecutor = new ThreadPoolExecutor(
            EVENT_THREADS,
            EVENT_THREADS,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(EVENT_QUEUE_CAPACITY),
            namedThreadFactory(),
            new ThreadPoolExecutor.DiscardPolicy()
    );

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        sendAsync(emitter, new ApiEvent("connected", "ok"));
        return emitter;
    }

    public void publish(String type) {
        publish(type, Map.of());
    }

    public void publish(String type, Object payload) {
        ApiEvent event = new ApiEvent(type, payload);
        for (SseEmitter emitter : emitters) {
            sendAsync(emitter, event);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void closeEmitters() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (IllegalStateException exception) {
                // The client may already have gone away while shutdown is in progress.
            }
        }
        emitters.clear();
    }

    @PreDestroy
    public void shutdown() {
        eventExecutor.shutdownNow();
    }

    private void sendAsync(SseEmitter emitter, ApiEvent event) {
        eventExecutor.execute(() -> send(emitter, event));
    }

    private void send(SseEmitter emitter, ApiEvent event) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event.type()).data(event.payload()));
            }
        } catch (IOException | IllegalStateException exception) {
            emitters.remove(emitter);
        }
    }

    private static ThreadFactory namedThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "cda-sse-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
