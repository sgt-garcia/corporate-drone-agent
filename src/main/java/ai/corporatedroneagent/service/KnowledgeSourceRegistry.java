package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Maps each {@link KnowledgeSource} to its {@link KnowledgeSourceAdapter}. Adding a source
 * type means adding an adapter bean — the engine, orchestrator, coordinator, and retrieval
 * never branch on source.
 */
@Service
public class KnowledgeSourceRegistry {

    private final Map<KnowledgeSource, KnowledgeSourceAdapter> adapters;

    public KnowledgeSourceRegistry(List<KnowledgeSourceAdapter> adapters) {
        this.adapters = adapters.stream()
                .collect(Collectors.toMap(KnowledgeSourceAdapter::source, Function.identity()));
    }

    public KnowledgeSourceAdapter adapterFor(KnowledgeSource source) {
        KnowledgeSourceAdapter adapter = adapters.get(source);
        if (adapter == null) {
            throw new IllegalStateException("No knowledge source adapter registered for " + source);
        }
        return adapter;
    }

    public boolean supports(KnowledgeSource source) {
        return adapters.containsKey(source);
    }
}
