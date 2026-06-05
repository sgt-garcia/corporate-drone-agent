package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.repository.KnowledgeResourceRepository;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeRootCleanupService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRootCleanupService.class);

    private final KnowledgeRootRepository rootRepository;
    private final KnowledgeResourceRepository resourceRepository;
    private final KnowledgeIndexingService indexingService;

    public KnowledgeRootCleanupService(
            KnowledgeRootRepository rootRepository,
            KnowledgeResourceRepository resourceRepository,
            KnowledgeIndexingService indexingService
    ) {
        this.rootRepository = rootRepository;
        this.resourceRepository = resourceRepository;
        this.indexingService = indexingService;
    }

    public void removeLocalFolderRoot(String reference) {
        if (reference == null || reference.isBlank()) {
            return;
        }

        rootRepository.findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, reference.trim())
                .ifPresent(this::removeRoot);
    }

    private void removeRoot(KnowledgeRoot root) {
        List<KnowledgeResource> resources = resourceRepository.findByRootId(root.getId());
        log.info(
                "Removing local knowledge root {} at {} with {} resources.",
                root.getId(),
                root.getReference(),
                resources.size()
        );
        resources.forEach(indexingService::deleteResource);
        rootRepository.delete(root.getId());
    }
}
