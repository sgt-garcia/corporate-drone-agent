package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The single, source-agnostic ingestion orchestrator. It resolves the adapter for a root's
 * source from the {@link KnowledgeSourceRegistry}, dedupes concurrent scans via the
 * coordinator, and runs the {@link KnowledgeScanEngine} — which owns the scan lifecycle,
 * status, SSE, cancellation, and stage pipeline. Replaces the per-source orchestrators; a
 * new source needs only an adapter bean.
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    // A small pool so several roots can scan at once (the user adds a few in a row) without
    // an unbounded fan-out at Atlass/Lucene. The Lucene writer is synchronized, so concurrent
    // scans serialize only at the index write.
    private static final int SCAN_THREADS = 4;

    private final KnowledgeScanEngine engine;
    private final KnowledgeSourceRegistry registry;
    private final KnowledgeScanCoordinator coordinator;
    private final KnowledgeRootRepository rootRepository;
    private final EventService eventService;
    private final ExecutorService scanExecutor;

    @Autowired
    public KnowledgeIngestionService(
            KnowledgeScanEngine engine,
            KnowledgeSourceRegistry registry,
            KnowledgeScanCoordinator coordinator,
            KnowledgeRootRepository rootRepository,
            EventService eventService
    ) {
        this(engine, registry, coordinator, rootRepository, eventService,
                Executors.newFixedThreadPool(SCAN_THREADS, namedThreadFactory("cda-knowledge-scan")));
    }

    KnowledgeIngestionService(
            KnowledgeScanEngine engine,
            KnowledgeSourceRegistry registry,
            KnowledgeScanCoordinator coordinator,
            KnowledgeRootRepository rootRepository,
            EventService eventService,
            ExecutorService scanExecutor
    ) {
        this.engine = engine;
        this.registry = registry;
        this.coordinator = coordinator;
        this.rootRepository = rootRepository;
        this.eventService = eventService;
        this.scanExecutor = scanExecutor;
    }

    /** Scan one root now (blocking), then return its refreshed state. Paused roots are skipped. */
    public KnowledgeRoot scan(KnowledgeRoot root) {
        UUID rootId = root.getId();
        if (root.isPaused()) {
            return root;
        }
        KnowledgeSourceAdapter adapter = registry.adapterFor(root.getSource());
        if (rootId != null && !coordinator.tryStartScan(rootId)) {
            // A scan for this root is already in flight (or being cancelled for a remove).
            return reload(root);
        }
        try {
            engine.scan(
                    root,
                    adapter,
                    () -> rootId != null && coordinator.isScanCancelled(rootId),
                    KnowledgeScanProgress.emitter(eventService, adapter.scanProgressId(root)));
        } finally {
            if (rootId != null) {
                coordinator.finishScan(rootId);
            }
        }
        return reload(root);
    }

    /**
     * Kick off a scan on a background thread and return immediately. Used by the controllers so
     * adding (or "Scan now") doesn't block the request — and the UI's add picker doesn't freeze —
     * for the whole index. The scanning/scanned status reaches the UI over SSE either way.
     */
    public void scanInBackground(KnowledgeRoot root) {
        scanExecutor.submit(() -> {
            try {
                scan(root);
            } catch (RuntimeException exception) {
                log.warn("Background knowledge scan failed for root {}.", root.getReference(), exception);
            }
        });
    }

    /** Scheduled sweep: scan every non-paused root across all sources, continuing past failures. */
    public void scanAll() {
        List<KnowledgeRoot> roots = rootRepository.findAll().stream()
                .filter(root -> registry.supports(root.getSource()))
                .filter(root -> !root.isPaused())
                .toList();
        log.info("Starting scheduled knowledge scan for {} roots.", roots.size());
        for (KnowledgeRoot root : roots) {
            try {
                scan(root);
            } catch (RuntimeException exception) {
                log.warn("Scheduled knowledge scan failed for root {}.", root.getReference(), exception);
            }
        }
        log.info("Finished scheduled knowledge scan for {} roots.", roots.size());
    }

    @PreDestroy
    void shutdown() {
        scanExecutor.shutdown();
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private KnowledgeRoot reload(KnowledgeRoot root) {
        return root.getId() == null ? root : rootRepository.findById(root.getId()).orElse(root);
    }
}
