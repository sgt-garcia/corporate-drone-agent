package ai.corporatedroneagent.job;

import ai.corporatedroneagent.service.KnowledgeFolderScanService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeFolderScanJob {

    private final KnowledgeFolderScanService scanService;

    public KnowledgeFolderScanJob(KnowledgeFolderScanService scanService) {
        this.scanService = scanService;
    }

    @Scheduled(cron = "0 0/15 * * * *")
    public void scanKnowledgeFolders() {
        scanService.scanAllFolders();
    }
}
