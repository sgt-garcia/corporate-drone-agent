package ai.corporatedroneagent.service;

/**
 * The full extracted text of a single knowledge resource, as returned by the fetch tool.
 * Unlike {@link KnowledgeContextSnippet} this carries the whole document (bounded by a cap),
 * not a query-centred passage; {@code truncated} and {@code fullLength} describe whether the
 * cap was hit and how long the original text was.
 */
public record KnowledgeDocument(
        String source,
        String rootName,
        String resourceReference,
        String resourceName,
        String content,
        boolean truncated,
        int fullLength
) {
}
