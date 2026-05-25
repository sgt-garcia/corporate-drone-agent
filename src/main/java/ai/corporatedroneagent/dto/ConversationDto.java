package ai.corporatedroneagent.dto;

import ai.corporatedroneagent.model.ConversationSettings;
import java.util.List;
import java.util.UUID;

public record ConversationDto(
        UUID id,
        UUID projectId,
        String name,
        ConversationSettings settings,
        List<MessageDto> messages
) {
}
