package ai.corporatedroneagent.service;

/**
 * A connected knowledge root described for discovery: which source it is, its display name,
 * how many documents it holds, the state of its last scan, and whether it is paused.
 */
public record KnowledgeSourceSummary(
        String source,
        String name,
        long resourceCount,
        String scanStatus,
        boolean paused
) {
}
