package ai.corporatedroneagent.dto;

import java.util.List;
import java.util.UUID;

public record ProjectDto(
        UUID id,
        String name,
        String workingFolder,
        String customInstructions,
        List<ConversationSummaryDto> conversations
) {
}
