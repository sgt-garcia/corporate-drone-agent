package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.MessageSourceDto;
import java.util.List;

public record ChatReply(String role, String content, List<MessageSourceDto> sources) {

    public static ChatReply assistant(String content) {
        return new ChatReply("assistant", content, List.of());
    }

    public static ChatReply assistant(String content, List<MessageSourceDto> sources) {
        return new ChatReply("assistant", content, sources == null ? List.of() : List.copyOf(sources));
    }

    public static ChatReply error(String content) {
        return new ChatReply("error", content, List.of());
    }

    public boolean assistant() {
        return "assistant".equals(role);
    }
}
