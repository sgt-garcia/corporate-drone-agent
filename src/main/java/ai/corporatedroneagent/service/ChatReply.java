package ai.corporatedroneagent.service;

public record ChatReply(String role, String content) {

    public static ChatReply assistant(String content) {
        return new ChatReply("assistant", content);
    }

    public static ChatReply error(String content) {
        return new ChatReply("error", content);
    }

    public boolean assistant() {
        return "assistant".equals(role);
    }
}
