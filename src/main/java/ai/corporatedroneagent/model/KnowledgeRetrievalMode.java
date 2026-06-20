package ai.corporatedroneagent.model;

/**
 * One configurable knowledge-retrieval mode (automatic RAG or the on-demand search tool):
 * whether it runs, how many results it pulls, and how many characters are kept from each.
 */
public class KnowledgeRetrievalMode {

    private boolean enabled;
    private int results;
    private int length;

    public KnowledgeRetrievalMode() {
    }

    public KnowledgeRetrievalMode(boolean enabled, int results, int length) {
        this.enabled = enabled;
        this.results = results;
        this.length = length;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getResults() {
        return results;
    }

    public void setResults(int results) {
        this.results = results;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }
}
