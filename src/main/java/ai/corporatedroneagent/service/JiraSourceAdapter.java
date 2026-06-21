package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeRootConfig;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.util.Timestamps;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The Jira-specific half of ingestion: enumerate a project's issues (optionally since a
 * cursor), read one issue's native JSON, render it to markdown. A self-resolving bean — it
 * pulls the connection/token from a {@link JiraConnectionResolver} and the project identity
 * from the root's config, so the generic {@link KnowledgeIngestionService} can drive it
 * knowing only the {@link KnowledgeRoot}. Jira reports timestamp-only change detection and
 * does not reconcile deletions, since an incremental enumeration only returns changed issues.
 */
@Service
public class JiraSourceAdapter implements KnowledgeSourceAdapter {

    private final JiraIssueFetchService issueFetchService;
    private final JiraConnectionResolver connectionResolver;

    public JiraSourceAdapter(JiraIssueFetchService issueFetchService, JiraConnectionResolver connectionResolver) {
        this.issueFetchService = issueFetchService;
        this.connectionResolver = connectionResolver;
    }

    @Override
    public KnowledgeSource source() {
        return KnowledgeSource.JIRA;
    }

    @Override
    public KnowledgeScanSession openSession(KnowledgeRoot root) {
        return new Session(connectionResolver.resolve(root), projectFromRoot(root));
    }

    private JiraProjectDto projectFromRoot(KnowledgeRoot root) {
        JiraProjectDto project = new JiraProjectDto();
        project.setId(JiraKnowledgeRootConfig.readProjectId(root));
        project.setKey(JiraKnowledgeRootConfig.readKey(root));
        project.setName(JiraKnowledgeRootConfig.readName(root));
        return project;
    }

    private final class Session implements KnowledgeScanSession {

        private final JiraConnection connection;
        private final JiraProjectDto project;

        private Session(JiraConnection connection, JiraProjectDto project) {
            this.connection = connection;
            this.project = project;
        }

        @Override
        public List<ResourceManifest> enumerate(ScanCursor cursor) {
            return issueFetchService.fetchProjectIssueManifests(
                            connection.instanceUrl(),
                            connection.email(),
                            connection.token(),
                            project,
                            cursor.updatedSince(),
                            connection.apiVersion()
                    ).stream()
                    // displayName carries the issue key for the progress ticker; the stored
                    // resource's display name comes from the fetched document instead.
                    .map(manifest -> new ResourceManifest(
                            manifest.reference(),
                            manifest.key(),
                            manifest.format(),
                            manifest.sizeBytes(),
                            manifest.lastModifiedAt(),
                            manifest))
                    .toList();
        }

        @Override
        public ReadResult read(ResourceManifest manifest) {
            JiraIssueFetchService.JiraIssueManifest issue = (JiraIssueFetchService.JiraIssueManifest) manifest.handle();
            JiraIssueFetchService.JiraIssueDocument document = issueFetchService.fetchIssueDocument(
                    connection.instanceUrl(), connection.email(), connection.token(), connection.apiVersion(), issue);
            return ReadResult.of(
                    document.reference(),
                    document.displayName(),
                    document.format(),
                    document.sizeBytes(),
                    document.lastModifiedAt(),
                    document.readValue());
        }

        @Override
        public ConversionResult convert(ReadResult read) {
            return ConversionResult.of(issueFetchService.toMarkdown(read.value()));
        }

        @Override
        public boolean isUnchanged(KnowledgeResource existing, ResourceManifest manifest) {
            return Timestamps.sameInstant(existing.getLastModifiedAt(), manifest.lastModifiedAt());
        }

        @Override
        public boolean reconcilesDeletes() {
            return false;
        }
    }
}
