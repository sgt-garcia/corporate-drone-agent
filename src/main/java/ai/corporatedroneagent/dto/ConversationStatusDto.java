package ai.corporatedroneagent.dto;

import java.util.UUID;

/**
 * Lightweight SSE payload for a conversation's run status changing, so the
 * sidebar can update its status indicator without re-fetching every project.
 */
public record ConversationStatusDto(
        UUID id,
        String status
) {
}
