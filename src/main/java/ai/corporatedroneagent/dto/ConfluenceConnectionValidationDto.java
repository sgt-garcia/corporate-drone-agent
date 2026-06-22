package ai.corporatedroneagent.dto;

public record ConfluenceConnectionValidationDto(
        boolean valid,
        String message
) {
}
