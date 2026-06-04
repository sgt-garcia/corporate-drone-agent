package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.repository.KnowledgeResourcePipelineRepository;
import ai.corporatedroneagent.repository.KnowledgeResourceRepository;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSearchService {

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
        return indexingService.search(query, limit).stream()
                .map(this::toSnippet)
                .flatMap(Optional::stream)
                .toList();
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
        return Optional.of(new KnowledgeContextSnippet(
                root.getSource() == null ? "" : root.getSource().name(),
                root.getDisplayName(),
                resource.get().getReference(),
                resource.get().getDisplayName(),
                chunk.get().getChunkIndex(),
                content,
                hit.score()
        ));
    }

    private boolean hasValidOffsets(KnowledgeResourceConversion conversion, KnowledgeResourceChunk chunk) {
        return chunk.getStartOffset() <= chunk.getEndOffset()
                && chunk.getEndOffset() <= conversion.getValue().length();
    }
}
