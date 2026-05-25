package ai.corporatedroneagent.dto;

public record ProjectRequest(
        String name,
        String workingFolder,
        String customInstructions
) {
}
