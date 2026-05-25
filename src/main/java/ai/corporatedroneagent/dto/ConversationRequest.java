package ai.corporatedroneagent.dto;

import ai.corporatedroneagent.model.ConversationSettings;

public record ConversationRequest(
        String name,
        ConversationSettings settings
) {
}
