package ai.corporatedroneagent.dto;

import java.util.List;
import java.util.UUID;

public record ConversationDto(
        UUID id,
        UUID projectId,
        String name,
        List<MessageDto> messages
) {
}
