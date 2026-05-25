package ai.corporatedroneagent.dto;

public record ApiEvent(
        String type,
        Object payload
) {
}
