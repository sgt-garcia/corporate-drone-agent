package ai.corporatedroneagent.dto;

public record JiraConnectionRequest(
        String instanceUrl,
        String email,
        String token,
        boolean clearToken
) {
}
