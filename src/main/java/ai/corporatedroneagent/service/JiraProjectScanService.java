package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.model.JiraSettings;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Orchestrates Jira project scans off the SettingsService monitor, mirroring how
 * {@link KnowledgeFolderScanService} orchestrates folder scans. SettingsService owns the
 * settings/root reads and writes ({@code jiraScanContext}, {@code currentJiraProjectDto},
 * and the root-settle helpers); this bean only sequences them around the low-level
 * scanner, so the dependency edge is one-way (this -&gt; SettingsService) with no cycle.
 */
@Service
public class JiraProjectScanService {

    private static final Logger log = LoggerFactory.getLogger(JiraProjectScanService.class);

    private final SettingsService settingsService;
    private final KnowledgeScanCoordinator knowledgeScanCoordinator;
    private final JiraKnowledgeScanService jiraKnowledgeScanService;

    public JiraProjectScanService(
            SettingsService settingsService,
            KnowledgeScanCoordinator knowledgeScanCoordinator,
            JiraKnowledgeScanService jiraKnowledgeScanService
    ) {
        this.settingsService = settingsService;
        this.knowledgeScanCoordinator = knowledgeScanCoordinator;
        this.jiraKnowledgeScanService = jiraKnowledgeScanService;
    }

    public JiraProjectDto scanProject(String projectId) {
        return scanProject(projectId, false);
    }

    public void scanAllProjects() {
        JiraSettings jira = settingsService.getJiraSettings();
        if (!jira.isConnected() || !jira.isTokenConfigured()) {
            log.debug("Skipping scheduled Jira project scan because Jira is not connected.");
            return;
        }
        List<String> projectIds = jira.getProjects().stream()
                .filter(project -> !"paused".equals(project.getStatus()))
                .map(JiraProjectDto::getId)
                .toList();
        log.info("Starting scheduled Jira scan for {} projects.", projectIds.size());
        for (String projectId : projectIds) {
            try {
                scanProject(projectId, true);
            } catch (RuntimeException exception) {
                log.warn("Scheduled Jira scan failed for project {}.", projectId, exception);
            }
        }
        log.info("Finished scheduled Jira scan for {} projects.", projectIds.size());
    }

    // Runs the scan without holding the SettingsService monitor for its duration:
    // jiraScanContext snapshots inputs and currentJiraProjectDto reads the result, so
    // getSettings (and the SSE-triggered refetch) stays responsive and observes the
    // "scanning" status the scan publishes — mirroring KnowledgeFolderScanService.
    private JiraProjectDto scanProject(String projectId, boolean scheduled) {
        SettingsService.JiraScanContext context = settingsService.jiraScanContext(projectId);

        if (jiraKnowledgeScanService == null) {
            // No scanner wired (tests / Jira indexing disabled): settle the root as an
            // instant success so the derived status becomes "scanned".
            return settingsService.recordSyntheticJiraScanSuccess(projectId, context);
        }
        if (context.paused()) {
            return settingsService.currentJiraProjectDto(projectId);
        }
        UUID rootId = context.rootId();
        if (rootId != null && !knowledgeScanCoordinator.tryStartJiraScan(rootId)) {
            // A scan for this project is already in flight; don't start a second one.
            return settingsService.currentJiraProjectDto(projectId);
        }
        try {
            if (scheduled) {
                jiraKnowledgeScanService.scanScheduledProject(
                        context.jira(), context.target(), context.token(), context.onProgress());
            } else {
                jiraKnowledgeScanService.scanProject(
                        context.jira(), context.target(), context.token(), context.onProgress());
            }
        } catch (RuntimeException exception) {
            // Settle the root as failed so the derived status is "error", even if the
            // scanner threw before recording it (mirrors the folder scan failure path).
            settingsService.recordJiraScanFailure(rootId, scanFailureMessage(exception));
            throw exception;
        } finally {
            if (rootId != null) {
                knowledgeScanCoordinator.finishJiraScan(rootId);
            }
        }
        return settingsService.currentJiraProjectDto(projectId);
    }

    private String scanFailureMessage(RuntimeException exception) {
        if (exception instanceof ResponseStatusException responseStatusException) {
            String reason = responseStatusException.getReason();
            if (reason != null && !reason.isBlank()) {
                return reason;
            }
        }
        return "Could not scan Jira project";
    }
}
