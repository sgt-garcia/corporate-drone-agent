package ai.corporatedroneagent.dto;

public record JiraConnectionValidationDto(
        boolean valid,
        String message,
        boolean liveValidationAvailable,
        String apiVersion
) {
}
