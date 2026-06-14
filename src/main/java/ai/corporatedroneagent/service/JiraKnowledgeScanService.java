package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeReferences;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.util.Strings;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * Runs a Jira project scan by handing a {@link JiraSourceAdapter} to the generic
 * {@link KnowledgeScanEngine}. The whole scan loop (read/convert/chunk/index, lifecycle,
 * incremental cursor, cancellation) lives in the engine; everything Jira-specific lives in
 * the adapter. This class just resolves the project's root and adapts the result type.
 */
@Service
public class JiraKnowledgeScanService {

    private static final Consumer<String> NO_PROGRESS = item -> {
    };
    private static final BooleanSupplier NOT_CANCELLED = () -> false;

    private final KnowledgeScanEngine engine;
    private final KnowledgeRootRepository rootRepository;
    private final JiraIssueFetchService issueFetchService;

    public JiraKnowledgeScanService(
            KnowledgeScanEngine engine,
            KnowledgeRootRepository rootRepository,
            JiraIssueFetchService issueFetchService
    ) {
        this.engine = engine;
        this.rootRepository = rootRepository;
        this.issueFetchService = issueFetchService;
    }

    public ScanResult scanProject(JiraSettings jira, JiraProjectDto project, String token) {
        return scanProject(jira, project, token, NO_PROGRESS);
    }

    public ScanResult scanProject(JiraSettings jira, JiraProjectDto project, String token, Consumer<String> onProgress) {
        return scanProject(jira, project, token, onProgress, NOT_CANCELLED);
    }

    public ScanResult scanProject(
            JiraSettings jira,
            JiraProjectDto project,
            String token,
            Consumer<String> onProgress,
            BooleanSupplier isCancelled
    ) {
        return run(jira, project, token, onProgress, isCancelled);
    }

    public ScanResult scanScheduledProject(JiraSettings jira, JiraProjectDto project, String token) {
        return scanScheduledProject(jira, project, token, NO_PROGRESS);
    }

    public ScanResult scanScheduledProject(
            JiraSettings jira,
            JiraProjectDto project,
            String token,
            Consumer<String> onProgress
    ) {
        return scanScheduledProject(jira, project, token, onProgress, NOT_CANCELLED);
    }

    public ScanResult scanScheduledProject(
            JiraSettings jira,
            JiraProjectDto project,
            String token,
            Consumer<String> onProgress,
            BooleanSupplier isCancelled
    ) {
        return run(jira, project, token, onProgress, isCancelled);
    }

    private ScanResult run(
            JiraSettings jira,
            JiraProjectDto project,
            String token,
            Consumer<String> onProgress,
            BooleanSupplier isCancelled
    ) {
        KnowledgeRoot root = knowledgeRoot(jira, project);
        JiraSourceAdapter adapter = new JiraSourceAdapter(issueFetchService, jira, project, token);
        try {
            KnowledgeScanEngine.ScanOutcome outcome = engine.scan(root, adapter, isCancelled, onProgress);
            return new ScanResult(outcome.resources(), outcome.bytes());
        } catch (KnowledgeScanEngine.KnowledgeScanException exception) {
            throw new JiraScanException(exception.getMessage(), exception.getCause());
        }
    }

    private KnowledgeRoot knowledgeRoot(JiraSettings jira, JiraProjectDto project) {
        String reference = JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), jiraProjectReferenceId(project));
        KnowledgeRoot root = rootRepository.findBySourceAndReference(KnowledgeSource.JIRA, reference)
                .orElseGet(KnowledgeRoot::new);
        root.setSource(KnowledgeSource.JIRA);
        root.setReference(reference);
        root.setDisplayName(jiraProjectDisplayName(project));
        return root;
    }

    private String jiraProjectReferenceId(JiraProjectDto project) {
        String projectId = Strings.defaultIfBlank(project.getId(), "").trim();
        return projectId.isBlank() ? project.getKey() : projectId;
    }

    private String jiraProjectDisplayName(JiraProjectDto project) {
        String key = Strings.defaultIfBlank(project.getKey(), "").trim();
        String name = Strings.defaultIfBlank(project.getName(), "").trim();
        if (key.isBlank()) {
            return name;
        }
        if (name.isBlank()) {
            return key;
        }
        return key + " - " + name;
    }

    public record ScanResult(long issues, long bytes) {
    }

    public static class JiraScanException extends RuntimeException {

        public JiraScanException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
