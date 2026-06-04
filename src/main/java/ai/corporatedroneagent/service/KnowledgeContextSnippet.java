package ai.corporatedroneagent.service;

public record KnowledgeContextSnippet(
        String source,
        String rootName,
        String resourceReference,
        String resourceName,
        int chunkIndex,
        String content,
        float score
) {
}
