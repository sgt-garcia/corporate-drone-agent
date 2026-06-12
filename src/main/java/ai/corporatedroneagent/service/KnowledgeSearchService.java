package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.repository.KnowledgeResourcePipelineRepository;
import ai.corporatedroneagent.repository.KnowledgeResourceRepository;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSearchService {

    private static final Pattern JIRA_ISSUE_KEY = Pattern.compile("\\b[A-Z][A-Z0-9_]+-\\d+\\b", Pattern.CASE_INSENSITIVE);

    private final KnowledgeIndexingService indexingService;
    private final KnowledgeResourcePipelineRepository pipelineRepository;
    private final KnowledgeResourceRepository resourceRepository;
    private final KnowledgeRootRepository rootRepository;

    public KnowledgeSearchService(
            KnowledgeIndexingService indexingService,
            KnowledgeResourcePipelineRepository pipelineRepository,
            KnowledgeResourceRepository resourceRepository,
            KnowledgeRootRepository rootRepository
    ) {
        this.indexingService = indexingService;
        this.pipelineRepository = pipelineRepository;
        this.resourceRepository = resourceRepository;
        this.rootRepository = rootRepository;
    }

    public List<KnowledgeContextSnippet> search(String query, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<KnowledgeContextSnippet> snippets = new ArrayList<>();
        Set<UUID> seenChunks = new LinkedHashSet<>();
        for (String issueKey : issueKeys(query)) {
            if (snippets.size() >= limit) {
                break;
            }
            snippets.addAll(jiraIssueSnippets(issueKey, limit - snippets.size(), seenChunks));
        }

        if (snippets.size() >= limit) {
            return snippets;
        }

        for (KnowledgeIndexingService.KnowledgeIndexHit hit : indexingService.search(query, limit)) {
            if (seenChunks.contains(hit.chunkId())) {
                continue;
            }
            Optional<KnowledgeContextSnippet> snippet = toSnippet(hit);
            if (snippet.isPresent()) {
                snippets.add(snippet.get());
                seenChunks.add(hit.chunkId());
            }
            if (snippets.size() >= limit) {
                break;
            }
        }
        return snippets;
    }

    private Optional<KnowledgeContextSnippet> toSnippet(KnowledgeIndexingService.KnowledgeIndexHit hit) {
        Optional<KnowledgeResourceChunk> chunk = pipelineRepository.findChunkById(hit.chunkId());
        if (chunk.isEmpty()) {
            return Optional.empty();
        }

        Optional<KnowledgeResource> resource = resourceRepository.findById(chunk.get().getResourceId())
                .filter(candidate -> !candidate.isDeleted());
        if (resource.isEmpty()) {
            return Optional.empty();
        }

        Optional<KnowledgeResourceConversion> conversion = pipelineRepository
                .findConversionByResourceId(resource.get().getId())
                .filter(candidate -> Boolean.TRUE.equals(candidate.getSuccess()));
        if (conversion.isEmpty() || !hasValidOffsets(conversion.get(), chunk.get())) {
            return Optional.empty();
        }

        KnowledgeRoot root = rootRepository.findById(resource.get().getRootId())
                .orElseGet(KnowledgeRoot::new);
        String content = conversion.get().getValue().substring(
                chunk.get().getStartOffset(),
                chunk.get().getEndOffset()
        );
        return Optional.of(toSnippet(root, resource.get(), chunk.get(), content, hit.score()));
    }

    private List<KnowledgeContextSnippet> jiraIssueSnippets(
            String issueKey,
            int limit,
            Set<UUID> seenChunks
    ) {
        List<KnowledgeContextSnippet> snippets = new ArrayList<>();
        for (KnowledgeResource resource : resourceRepository.findActiveBySourceAndDisplayNamePrefix(
                KnowledgeSource.JIRA,
                issueKey,
                limit
        )) {
            Optional<KnowledgeResourceConversion> conversion = pipelineRepository
                    .findConversionByResourceId(resource.getId())
                    .filter(candidate -> Boolean.TRUE.equals(candidate.getSuccess()));
            if (conversion.isEmpty()) {
                continue;
            }
            KnowledgeRoot root = rootRepository.findById(resource.getRootId()).orElseGet(KnowledgeRoot::new);
            for (KnowledgeResourceChunk chunk : pipelineRepository.findChunksByResourceId(resource.getId())) {
                if (snippets.size() >= limit) {
                    return snippets;
                }
                if (seenChunks.contains(chunk.getId()) || !hasValidOffsets(conversion.get(), chunk)) {
                    continue;
                }
                String content = conversion.get().getValue().substring(chunk.getStartOffset(), chunk.getEndOffset());
                snippets.add(toSnippet(root, resource, chunk, content, Float.MAX_VALUE));
                seenChunks.add(chunk.getId());
            }
        }
        return snippets;
    }

    private KnowledgeContextSnippet toSnippet(
            KnowledgeRoot root,
            KnowledgeResource resource,
            KnowledgeResourceChunk chunk,
            String content,
            float score
    ) {
        return new KnowledgeContextSnippet(
                root.getSource() == null ? "" : root.getSource().name(),
                root.getDisplayName(),
                resource.getReference(),
                resource.getDisplayName(),
                chunk.getChunkIndex(),
                content,
                score
        );
    }

    private Set<String> issueKeys(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = JIRA_ISSUE_KEY.matcher(query);
        while (matcher.find()) {
            keys.add(matcher.group().toUpperCase(java.util.Locale.ROOT));
        }
        return keys;
    }

    private boolean hasValidOffsets(KnowledgeResourceConversion conversion, KnowledgeResourceChunk chunk) {
        return chunk.getStartOffset() <= chunk.getEndOffset()
                && chunk.getEndOffset() <= conversion.getValue().length();
    }
}
