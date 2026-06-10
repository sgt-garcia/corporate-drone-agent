package ai.corporatedroneagent.service;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Streams the name of the resource currently being scanned to the UI. The
 * frontend shows it in the per-source scan ticker so a running scan names the
 * local file or Jira ticket it is working on, rather than a static placeholder.
 *
 * <p>A scan can touch thousands of resources, so emission is throttled per
 * source: the first item is always sent, then at most one item per interval.
 * Dropped items are harmless because the ticker only needs the current name.
 */
final class KnowledgeScanProgress {

    static final String EVENT = "knowledge-scan-progress";

    private static final long MIN_INTERVAL_MILLIS = 120;

    private KnowledgeScanProgress() {
    }

    /**
     * Returns a progress consumer bound to a single scan. It is used from the
     * scanning thread only, so it keeps its throttle state without
     * synchronization.
     *
     * @param eventService the SSE publisher
     * @param sourceId     the folder id or Jira project id the UI rows key on
     */
    static Consumer<String> emitter(EventService eventService, String sourceId) {
        return new Consumer<>() {
            private long lastEmitMillis;

            @Override
            public void accept(String item) {
                if (item == null || item.isBlank()) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (lastEmitMillis != 0 && now - lastEmitMillis < MIN_INTERVAL_MILLIS) {
                    return;
                }
                lastEmitMillis = now;
                eventService.publish(EVENT, Map.of("id", sourceId, "item", item));
            }
        };
    }
}
