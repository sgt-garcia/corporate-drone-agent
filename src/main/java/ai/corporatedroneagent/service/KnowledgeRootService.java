package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.util.Strings;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeRootService {

    private final KnowledgeRootRepository repository;

    public KnowledgeRootService(KnowledgeRootRepository repository) {
        this.repository = repository;
    }

    public List<KnowledgeRoot> listRoots() {
        return repository.findAll();
    }

    public KnowledgeRoot createRoot(KnowledgeSource source, String reference, String displayName, String configJson) {
        if (source == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Knowledge source is required");
        }
        String normalizedReference = Strings.defaultIfBlank(reference, "").trim();
        if (normalizedReference.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Knowledge root reference is required");
        }
        if (repository.findBySourceAndReference(source, normalizedReference).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Knowledge root is already configured");
        }

        KnowledgeRoot root = new KnowledgeRoot();
        root.setSource(source);
        root.setReference(normalizedReference);
        root.setDisplayName(Strings.defaultIfBlank(displayName, normalizedReference).trim());
        root.setConfigJson(Strings.defaultIfBlank(configJson, ""));
        return repository.save(root);
    }

    public KnowledgeRoot pauseRoot(UUID id) {
        KnowledgeRoot root = findRoot(id);
        root.setPaused(true);
        return repository.save(root);
    }

    public KnowledgeRoot resumeRoot(UUID id) {
        KnowledgeRoot root = findRoot(id);
        root.setPaused(false);
        return repository.save(root);
    }

    public void deleteRoot(UUID id) {
        if (repository.findById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge root not found");
        }
        repository.delete(id);
    }

    private KnowledgeRoot findRoot(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge root not found"));
    }
}
