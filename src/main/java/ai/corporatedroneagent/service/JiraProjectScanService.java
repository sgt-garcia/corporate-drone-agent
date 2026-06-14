package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeRootConfig;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Orchestrates Jira project scans, mirroring {@link KnowledgeFolderScanService}: it
 * resolves the root, marks scan state, runs the low-level scanner, and settles the
 * root — all through {@link KnowledgeRootRepository} directly. It only reads scan
 * inputs from {@link SettingsService} (the Jira connection + token, which that service
 * owns) and uses its non-synchronized {@code jiraProject(root)} mapper for return DTOs.
 *
 * <p>Because the per-project scan touches no synchronized SettingsService method once a
 * scan is registered, a concurrent {@code removeJiraProject} can hold the SettingsService
 * monitor while waiting for the scan to stop without any risk of deadlock — exactly the
 * property the folder scan bean has.
 */
@Service
public class JiraProjectScanService {

    private static final Logger log = LoggerFactory.getLogger(JiraProjectScanService.class);

    private final SettingsService settingsService;
    private final KnowledgeRootRepository knowledgeRootRepository;
    private final EventService eventService;
    private final KnowledgeScanCoordinator knowledgeScanCoordinator;
    private final JiraKnowledgeScanService jiraKnowledgeScanService;

    public JiraProjectScanService(
            SettingsService settingsService,
            KnowledgeRootRepository knowledgeRootRepository,
            EventService eventService,
            KnowledgeScanCoordinator knowledgeScanCoordinator,
            JiraKnowledgeScanService jiraKnowledgeScanService
    ) {
        this.settingsService = settingsService;
        this.knowledgeRootRepository = knowledgeRootRepository;
        this.eventService = eventService;
        this.knowledgeScanCoordinator = knowledgeScanCoordinator;
        this.jiraKnowledgeScanService = jiraKnowledgeScanService;
    }

    public JiraProjectDto scanProject(String projectId) {
        JiraSettings connection = settingsService.getJiraSettings();
        requireConnected(connection);
        KnowledgeRoot root = findJiraRoot(projectId);
        return scan(root, connection, settingsService.savedJiraToken(), false);
    }

    public void scanAllProjects() {
        JiraSettings connection = settingsService.getJiraSettings();
        if (!connection.isConnected() || !connection.isTokenConfigured()) {
            log.debug("Skipping scheduled Jira project scan because Jira is not connected.");
            return;
        }
        String token = settingsService.savedJiraToken();
        List<KnowledgeRoot> roots = knowledgeRootRepository.findBySource(KnowledgeSource.JIRA);
        log.info("Starting scheduled Jira scan for {} projects.", roots.size());
        for (KnowledgeRoot root : roots) {
            if (root.isPaused()) {
                continue;
            }
            try {
                scan(root, connection, token, true);
            } catch (RuntimeException exception) {
                log.warn("Scheduled Jira scan failed for project {}.", root.getReference(), exception);
            }
        }
        log.info("Finished scheduled Jira scan for {} projects.", roots.size());
    }

    private JiraProjectDto scan(KnowledgeRoot root, JiraSettings connection, String token, boolean scheduled) {
        if (jiraKnowledgeScanService == null) {
            // No scanner wired (tests / Jira indexing disabled): settle the root as an
            // instant success so the derived status becomes "scanned".
            settleSyntheticSuccess(root.getId());
            return settingsService.jiraProject(reload(root));
        }
        if (root.isPaused()) {
            return settingsService.jiraProject(root);
        }
        UUID rootId = root.getId();
        if (!knowledgeScanCoordinator.tryStartJiraScan(rootId)) {
            // A scan for this project is already in flight (or being cancelled for a
            // remove); don't start a second one.
            return settingsService.jiraProject(reload(root));
        }
        JiraProjectDto target = settingsService.jiraProject(root);
        Consumer<String> onProgress = KnowledgeScanProgress.emitter(eventService, target.getId());
        BooleanSupplier isCancelled = () -> knowledgeScanCoordinator.isJiraScanCancelled(rootId);
        try {
            if (scheduled) {
                jiraKnowledgeScanService.scanScheduledProject(connection, target, token, onProgress, isCancelled);
            } else {
                jiraKnowledgeScanService.scanProject(connection, target, token, onProgress, isCancelled);
            }
        } catch (RuntimeException exception) {
            // Settle the root as failed so the derived status is "error", even if the
            // scanner threw before recording it (mirrors the folder scan failure path).
            settleFailure(rootId, scanFailureMessage(exception));
            throw exception;
        } finally {
            knowledgeScanCoordinator.finishJiraScan(rootId);
        }
        return settingsService.jiraProject(reload(root));
    }

    private KnowledgeRoot findJiraRoot(String projectId) {
        return knowledgeRootRepository.findBySource(KnowledgeSource.JIRA).stream()
                .filter(root -> projectId.equals(JiraKnowledgeRootConfig.readProjectId(root)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira project not found"));
    }

    private KnowledgeRoot reload(KnowledgeRoot root) {
        return knowledgeRootRepository.findById(root.getId()).orElse(root);
    }

    private void settleSyntheticSuccess(UUID rootId) {
        knowledgeRootRepository.findById(rootId).ifPresent(root -> {
            Instant now = Instant.now();
            root.setScanStatus(WorkStatus.DONE);
            root.setScanSuccess(true);
            root.setScanMessage("");
            root.setScanStartedAt(now);
            root.setScanFinishedAt(now);
            knowledgeRootRepository.save(root);
        });
        publishSettingsUpdated();
    }

    // Settle the root as a failed scan, preserving a pause that landed mid-scan and
    // no-op'ing if a concurrent remove already deleted the root.
    private void settleFailure(UUID rootId, String message) {
        knowledgeRootRepository.findById(rootId).ifPresent(root -> {
            root.setScanStatus(WorkStatus.DONE);
            root.setScanSuccess(false);
            root.setScanMessage(message);
            root.setScanFinishedAt(Instant.now());
            knowledgeRootRepository.save(root);
        });
        publishSettingsUpdated();
    }

    private void publishSettingsUpdated() {
        eventService.publish("settings-updated");
    }

    private void requireConnected(JiraSettings connection) {
        if (!connection.isConnected() || !connection.isTokenConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Save Jira setup before managing projects");
        }
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
