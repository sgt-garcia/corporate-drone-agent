package ai.corporatedroneagent.job;

import ai.corporatedroneagent.service.JiraProjectScanService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JiraKnowledgeScanJob {

    private final JiraProjectScanService jiraProjectScanService;

    public JiraKnowledgeScanJob(JiraProjectScanService jiraProjectScanService) {
        this.jiraProjectScanService = jiraProjectScanService;
    }

    @Scheduled(cron = "0 0/15 * * * *")
    public void scanJiraProjects() {
        jiraProjectScanService.scanAllProjects();
    }
}
