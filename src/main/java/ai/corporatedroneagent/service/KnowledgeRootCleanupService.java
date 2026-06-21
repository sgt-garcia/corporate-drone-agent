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
        removeRoot(KnowledgeSource.LOCAL_FOLDER, reference);
    }

    public void removeRoot(KnowledgeSource source, String reference) {
        if (source == null || reference == null || reference.isBlank()) {
            return;
        }

        rootRepository.findBySourceAndReference(source, reference.trim())
                .ifPresent(this::removeRoot);
    }

    public void removeRoot(KnowledgeRoot root) {
        if (root == null || root.getId() == null) {
            return;
        }
        List<KnowledgeResource> resources = resourceRepository.findByRootId(root.getId());
        log.info(
                "Removing {} knowledge root {} at {} with {} resources.",
                root.getSource(),
                root.getId(),
                root.getReference(),
                resources.size()
        );
        resources.forEach(indexingService::deleteResource);
        rootRepository.delete(root.getId());
    }
}
