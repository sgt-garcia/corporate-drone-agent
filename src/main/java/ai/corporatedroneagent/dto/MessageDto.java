package ai.corporatedroneagent.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(
        UUID id,
        String role,
        String content,
        Instant createdAt
) {
}
