package ai.corporatedroneagent.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectDto(
        UUID id,
        String name,
        String workingFolder,
        String customInstructions,
        Instant createdAt,
        List<ConversationSummaryDto> conversations
) {
}
