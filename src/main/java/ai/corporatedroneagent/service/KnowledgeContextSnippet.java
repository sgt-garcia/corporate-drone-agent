package ai.corporatedroneagent.service;

public record KnowledgeContextSnippet(
        String source,
        String rootName,
        String resourceReference,
        String resourceName,
        String content,
        float score
) {
}
