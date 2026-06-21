package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final KnowledgeScanEngine engine;
    private final KnowledgeSourceRegistry registry;
    private final KnowledgeScanCoordinator coordinator;
    private final KnowledgeRootRepository rootRepository;
    private final EventService eventService;

    public KnowledgeIngestionService(
            KnowledgeScanEngine engine,
            KnowledgeSourceRegistry registry,
            KnowledgeScanCoordinator coordinator,
            KnowledgeRootRepository rootRepository,
            EventService eventService
    ) {
        this.engine = engine;
        this.registry = registry;
        this.coordinator = coordinator;
        this.rootRepository = rootRepository;
        this.eventService = eventService;
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

    private KnowledgeRoot reload(KnowledgeRoot root) {
        return root.getId() == null ? root : rootRepository.findById(root.getId()).orElse(root);
    }
}
