package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import java.time.Instant;
import java.util.List;

/**
 * The Jira-specific half of ingestion: enumerate a project's issues (optionally since a
 * cursor), read one issue's native JSON, render it to markdown. Constructed per scan with
 * the resolved connection + token + target project; the {@link KnowledgeScanEngine} drives
 * it. Jira reports timestamp-only change detection and does not reconcile deletions, since
 * an incremental enumeration only ever returns changed issues.
 */
public class JiraSourceAdapter implements KnowledgeSourceAdapter {

    private final JiraIssueFetchService issueFetchService;
    private final JiraSettings jira;
    private final JiraProjectDto project;
    private final String token;

    public JiraSourceAdapter(
            JiraIssueFetchService issueFetchService,
            JiraSettings jira,
            JiraProjectDto project,
            String token
    ) {
        this.issueFetchService = issueFetchService;
        this.jira = jira;
        this.project = project;
        this.token = token;
    }

    @Override
    public KnowledgeSource source() {
        return KnowledgeSource.JIRA;
    }

    @Override
    public KnowledgeScanSession openSession(KnowledgeRoot root) {
        return new Session();
    }

    private final class Session implements KnowledgeScanSession {

        @Override
        public List<ResourceManifest> enumerate(ScanCursor cursor) {
            return issueFetchService.fetchProjectIssueManifests(
                            jira.getInstanceUrl(),
                            jira.getEmail(),
                            token,
                            project,
                            cursor.updatedSince(),
                            jira.getApiVersion()
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
                    jira.getInstanceUrl(), jira.getEmail(), token, jira.getApiVersion(), issue);
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
            return sameTimestamp(existing.getLastModifiedAt(), manifest.lastModifiedAt());
        }

        @Override
        public boolean reconcilesDeletes() {
            return false;
        }
    }

    private static boolean sameTimestamp(Instant first, Instant second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.toEpochMilli() == second.toEpochMilli();
    }
}
