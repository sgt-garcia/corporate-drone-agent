package ai.corporatedroneagent.job;

import ai.corporatedroneagent.service.SettingsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JiraKnowledgeScanJob {

    private final SettingsService settingsService;

    public JiraKnowledgeScanJob(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Scheduled(cron = "0 0/15 * * * *")
    public void scanJiraProjects() {
        settingsService.scanAllJiraProjects();
    }
}
