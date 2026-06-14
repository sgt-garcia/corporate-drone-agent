package ai.corporatedroneagent.job;

import ai.corporatedroneagent.service.KnowledgeIngestionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically re-scans every knowledge root (all sources) through the ingestion service. */
@Component
public class KnowledgeScanJob {

    private final KnowledgeIngestionService ingestionService;

    public KnowledgeScanJob(KnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Scheduled(cron = "0 0/15 * * * *")
    public void scanKnowledgeRoots() {
        ingestionService.scanAll();
    }
}
