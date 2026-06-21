package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ConfluenceSpaceDto;
import ai.corporatedroneagent.model.knowledge.ConfluenceKnowledgeRootConfig;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.util.Timestamps;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The Confluence-specific half of ingestion: enumerate a space's pages (optionally since a
 * cursor), read one page's native JSON, render it to markdown. A self-resolving bean — it
 * pulls the connection/token from a {@link ConfluenceConnectionResolver} and the space
 * identity from the root's config, so the generic {@link KnowledgeIngestionService} can drive
 * it knowing only the {@link KnowledgeRoot}. Sibling of {@link JiraSourceAdapter}: Confluence
 * reports timestamp-only change detection and does not reconcile deletions, since an
 * incremental enumeration only returns changed pages.
 */
@Service
public class ConfluenceSourceAdapter implements KnowledgeSourceAdapter {

    private final ConfluencePageFetchService pageFetchService;
    private final ConfluenceConnectionResolver connectionResolver;

    public ConfluenceSourceAdapter(
            ConfluencePageFetchService pageFetchService,
            ConfluenceConnectionResolver connectionResolver
    ) {
        this.pageFetchService = pageFetchService;
        this.connectionResolver = connectionResolver;
    }

    @Override
    public KnowledgeSource source() {
        return KnowledgeSource.CONFLUENCE;
    }

    @Override
    public KnowledgeScanSession openSession(KnowledgeRoot root) {
        return new Session(connectionResolver.resolve(root), spaceFromRoot(root));
    }

    @Override
    public String scanProgressId(KnowledgeRoot root) {
        // A Confluence space's settings row keys on the external space id, not the root id.
        return ConfluenceKnowledgeRootConfig.readSpaceId(root);
    }

    private ConfluenceSpaceDto spaceFromRoot(KnowledgeRoot root) {
        ConfluenceSpaceDto space = new ConfluenceSpaceDto();
        space.setId(ConfluenceKnowledgeRootConfig.readSpaceId(root));
        space.setKey(ConfluenceKnowledgeRootConfig.readKey(root));
        space.setName(ConfluenceKnowledgeRootConfig.readName(root));
        return space;
    }

    private final class Session implements KnowledgeScanSession {

        private final ConfluenceConnection connection;
        private final ConfluenceSpaceDto space;

        private Session(ConfluenceConnection connection, ConfluenceSpaceDto space) {
            this.connection = connection;
            this.space = space;
        }

        @Override
        public List<ResourceManifest> enumerate(ScanCursor cursor) {
            return pageFetchService.fetchSpacePageManifests(
                            connection.instanceUrl(),
                            connection.email(),
                            connection.token(),
                            space,
                            cursor.updatedSince()
                    ).stream()
                    // displayName carries the page title for the progress ticker; the stored
                    // resource's display name comes from the fetched document instead.
                    .map(manifest -> new ResourceManifest(
                            manifest.reference(),
                            manifest.displayName(),
                            manifest.format(),
                            manifest.sizeBytes(),
                            manifest.lastModifiedAt(),
                            manifest))
                    .toList();
        }

        @Override
        public ReadResult read(ResourceManifest manifest) {
            ConfluencePageFetchService.ConfluencePageManifest page =
                    (ConfluencePageFetchService.ConfluencePageManifest) manifest.handle();
            ConfluencePageFetchService.ConfluencePageDocument document = pageFetchService.fetchPageDocument(
                    connection.instanceUrl(), connection.email(), connection.token(), page);
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
            return ConversionResult.of(pageFetchService.toMarkdown(read.value()));
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
