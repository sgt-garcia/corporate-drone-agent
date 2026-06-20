package ai.corporatedroneagent.model;

/**
 * Configuration for how the agent draws on connected knowledge sources. Two independent
 * modes share the same sources: {@code auto} injects snippets into the prompt on every
 * message (RAG), and {@code search} is the tool the model can call on demand. Either,
 * both, or neither may be enabled.
 */
public class KnowledgeToolSettings {

    private KnowledgeRetrievalMode auto = new KnowledgeRetrievalMode(true, 10, 3000);
    private KnowledgeRetrievalMode search = new KnowledgeRetrievalMode(false, 10, 3000);

    public KnowledgeRetrievalMode getAuto() {
        return auto;
    }

    public void setAuto(KnowledgeRetrievalMode auto) {
        this.auto = auto == null ? new KnowledgeRetrievalMode(true, 10, 3000) : auto;
    }

    public KnowledgeRetrievalMode getSearch() {
        return search;
    }

    public void setSearch(KnowledgeRetrievalMode search) {
        this.search = search == null ? new KnowledgeRetrievalMode(false, 10, 3000) : search;
    }
}
