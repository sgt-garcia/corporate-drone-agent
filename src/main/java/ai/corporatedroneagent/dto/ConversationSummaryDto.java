package ai.corporatedroneagent.dto;

import java.util.UUID;

public record ConversationSummaryDto(
        UUID id,
        UUID projectId,
        String name
) {
}
