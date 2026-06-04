package ai.corporatedroneagent.dto;

import java.time.Instant;

public record ApiErrorDto(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
