package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ApiEvent;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class EventService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        send(emitter, new ApiEvent("connected", "ok"));
        return emitter;
    }

    public void publish(String type, Object payload) {
        ApiEvent event = new ApiEvent(type, payload);
        for (SseEmitter emitter : emitters) {
            send(emitter, event);
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

    private void send(SseEmitter emitter, ApiEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(event.payload()));
        } catch (IOException | IllegalStateException exception) {
            emitters.remove(emitter);
        }
    }
}
