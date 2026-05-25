package ai.corporatedroneagent.dto;

import java.util.UUID;

public record MessageEventDto(
        UUID conversationId,
        MessageDto message
) {
}
